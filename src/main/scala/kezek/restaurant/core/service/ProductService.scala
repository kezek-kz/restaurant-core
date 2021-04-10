package kezek.restaurant.core.service

import akka.Done
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.amazonaws.services.s3.AmazonS3
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.scalaland.chimney.dsl.TransformerOps
import kezek.restaurant.core.aws.{AwsS3Client, S3Client}
import kezek.restaurant.core.codec.MainCodec
import kezek.restaurant.core.domain.CategoryFilter.ByMultipleIdsFilter
import kezek.restaurant.core.domain.ProductFilter._
import kezek.restaurant.core.domain._
import kezek.restaurant.core.domain.dto.{CreateProductDTO, ProductDTO, ProductListWithTotalDTO, UpdateProductDTO}
import kezek.restaurant.core.exception.ApiException
import kezek.restaurant.core.repository.mongo.MongoRepository.DUPLICATED_KEY_ERROR_CODE
import kezek.restaurant.core.repository.mongo.{CategoryMongoRepository, ProductMongoRepository}
import kezek.restaurant.core.repository.{CategoryRepository, ProductRepository}
import kezek.restaurant.core.util.{ASC, SortType}
import org.mongodb.scala.{MongoClient, MongoWriteException}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

object ProductService extends MainCodec {

  def generateFilters(categoryId: Option[String] = None,
                      title: Option[String] = None,
                      description: Option[String] = None): Seq[ProductFilter] = {
    var filters: Seq[ProductFilter] = Seq.empty
    if (categoryId.isDefined) filters = filters :+ ByCategoryIdFilter(categoryId.get)
    if (title.isDefined) filters = filters :+ ByTitleFilter(title.get)
    if (description.isDefined) filters = filters :+ ByDescriptionFilter(description.get)
    filters
  }

}

class ProductService()(implicit val mongoClient: MongoClient,
                       implicit val executionContext: ExecutionContext,
                       implicit val system: ActorSystem[_],
                       implicit val s3Client: AmazonS3) extends MainCodec {

  val config: Config = ConfigFactory.load()
  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)
  val productRepository: ProductRepository = new ProductMongoRepository()
  val categoryRepository: CategoryRepository = new CategoryMongoRepository()
  val productBucket: S3Client = new AwsS3Client("kezek")

  def paginate(filters: Seq[ProductFilter],
               page: Option[Int],
               pageSize: Option[Int],
               sortParams: Map[String, SortType]): Future[ProductListWithTotalDTO] = {
    log.debug(s"paginate() was called {filters: $filters, page: $page, pageSize: $pageSize, sortParams: $sortParams}")
    (for (
      products <- productRepository.paginate(filters, page, pageSize, sortParams);
      productDTOs <- attachCategoryToProduct(products);
      count <- productRepository.count(filters)
    ) yield ProductListWithTotalDTO(
      collection = productDTOs,
      total = count
    )).recover { exception =>
      log.error(s"paginate() failed to paginate products {exception: $exception, filters: $filters, page: $page, pageSize: $pageSize, sortParams: $sortParams}")
      throw new RuntimeException(s"Failed to paginate products: $exception")
    }
  }


  def update(id: String, updateProductDTO: UpdateProductDTO): Future[ProductDTO] = {
    log.debug(s"update() was called {id: $id, updateProductDTO: $updateProductDTO}")
    val product = updateProductDTO.into[Product].withFieldConst(_.id, id).transform
    productRepository.update(id, product).flatMap(attachCategoryToProduct)
  }

  def getById(id: String): Future[ProductDTO] = {
    log.debug(s"getById() was called {id: $id}")
    productRepository.findById(id).flatMap {
      case Some(product) => attachCategoryToProduct(product)
      case None =>
        log.error(s"getById() failed to find product {id: $id}")
        throw ApiException(StatusCodes.NotFound, s"Failed to find product with id: $id")
    }
  }

  def create(createProductDTO: CreateProductDTO): Future[ProductDTO] = {
    log.debug(s"create() was called {createProductDTO: ${createProductDTO.asJson.noSpaces}}")
    val product = createProductDTO.into[Product].withFieldConst(_.id, UUID.randomUUID().toString).transform
    productRepository.create(product).recover {
      case ex: MongoWriteException if ex.getCode == DUPLICATED_KEY_ERROR_CODE =>
        log.error(s"create() failed to create product due to duplicate key {ex: $ex, product: ${product.asJson.noSpaces}")
        throw ApiException(StatusCodes.Conflict, s"Failed to create, product with id: ${product.id} already exists")
      case ex: Exception =>
        log.error(s"create() failed to create product {ex: $ex, product: ${product.asJson.noSpaces}}")
        throw ApiException(StatusCodes.ServiceUnavailable, ex.getMessage)
    } flatMap {
      attachCategoryToProduct
    }
  }

  def delete(id: String): Future[Done] = {
    log.debug(s"delete() was called {id: $id}")
    productRepository.delete(id)
  }

  def uploadProductImage(byteSource: Source[ByteString, Any],
                         productId: String,
                         fileInfo: FileInfo): Future[ProductDTO] = {
    log.debug(s"uploadProductImage() was called {productId: $productId, fileName: ${fileInfo.fileName}, contentType: ${fileInfo.contentType.toString()}}")
    getById(productId).flatMap { product =>
      productBucket.upload(byteSource, productId, fileInfo).flatMap { imageUrl =>
        productRepository.update(
          productId,
          product.copy(image = Some(imageUrl)).into[Product]
            .withFieldConst(_.categories, product.categories.map(_.id))
            .transform
        ) flatMap attachCategoryToProduct
      }
    }
  }

  def deleteProductImage(productId: String): Future[ProductDTO] = {
    log.debug(s"deleteProductImage() was called {productId: $productId}")
    getById(productId).flatMap { product =>
      if(product.image.isDefined) {
        productBucket.delete(productId).flatMap { _ =>
          productRepository.update(
            productId,
            product.copy(image = None).into[Product]
              .withFieldConst(_.categories, product.categories.map(_.id))
              .transform
          ) flatMap attachCategoryToProduct
        }
      } else {
        log.error(s"deleteProductImage() failed to find product image {id: $productId}")
        throw ApiException(StatusCodes.NotFound, "Failed to find product image to delete")
      }
    }
  }

  private def attachCategoryToProduct(product: Product): Future[ProductDTO] = {
    categoryRepository.findAll(Seq(ByMultipleIdsFilter(product.categories)), Map("title" -> ASC)).map {
      categories => {
        product.into[ProductDTO].withFieldConst(_.categories, categories).transform
      }
    }
  }

  private def attachCategoryToProduct(products: Seq[Product]): Future[Seq[ProductDTO]] = {
    Future.sequence { products map attachCategoryToProduct }
  }
}

