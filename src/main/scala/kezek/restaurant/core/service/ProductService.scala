package kezek.restaurant.core.service

import akka.Done
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.scalaland.chimney.dsl.TransformerOps
import kezek.restaurant.core.codec.MainCodec
import kezek.restaurant.core.domain.ProductFilter._
import kezek.restaurant.core.domain._
import kezek.restaurant.core.domain.dto.{CreateProductDTO, ProductListWithTotalDTO, UpdateProductDTO}
import kezek.restaurant.core.exception.ApiException
import kezek.restaurant.core.repository.ProductRepository
import kezek.restaurant.core.repository.mongo.ProductMongoRepository
import kezek.restaurant.core.repository.mongo.MongoRepository.DUPLICATED_KEY_ERROR_CODE
import kezek.restaurant.core.util.SortType
import org.mongodb.scala.{MongoClient, MongoWriteException}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

object ProductService extends MainCodec {

  def generateFilters(firstName: Option[String],
                      lastName: Option[String],
                      email: Option[String],
                      phoneNumber: Option[String]): Seq[ProductFilter] = {
    var filters: Seq[ProductFilter] = Seq.empty
    if (firstName.isDefined) filters = filters :+ ByFirstNameFilter(firstName.get)
    if (lastName.isDefined) filters = filters :+ ByLastNameFilter(lastName.get)
    if (email.isDefined) filters = filters :+ ByEmailFilter(email.get)
    if (phoneNumber.isDefined) filters = filters :+ ByPhoneNumberFilter(phoneNumber.get)
    filters
  }

}

class ProductService()(implicit val mongoClient: MongoClient,
                        implicit val executionContext: ExecutionContext,
                        implicit val system: ActorSystem[_]) extends MainCodec {

  val config: Config = ConfigFactory.load()
  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)
  val productRepository: ProductRepository = new ProductMongoRepository()

  def paginate(filters: Seq[ProductFilter],
               page: Option[Int],
               pageSize: Option[Int],
               sortParams: Map[String, SortType]): Future[ProductListWithTotalDTO] = {
    log.debug(s"paginate() was called {filters: $filters, page: $page, pageSize: $pageSize, sortParams: $sortParams}")
    (for (
      products <- productRepository.paginate(filters, page, pageSize, sortParams);
      count <- productRepository.count(filters)
    ) yield ProductListWithTotalDTO(
      collection = products,
      total = count
    )).recover { exception =>
      log.error(s"paginate() failed to paginate products {exception: $exception, filters: $filters, page: $page, pageSize: $pageSize, sortParams: $sortParams}")
      throw new RuntimeException(s"Failed to paginate products: $exception")
    }
  }


  def update(id: String, updateProductDTO: UpdateProductDTO): Future[Product] = {
    log.debug(s"update() was called {id: $id, updateProductDTO: $updateProductDTO}")
    val product = updateProductDTO.into[Product].withFieldConst(_.id, id).transform
    productRepository.update(id, product)
  }

  def getById(id: String): Future[Product] = {
    log.debug(s"getById() was called {id: $id}")
    productRepository.findById(id).map {
      case Some(product) => product
      case None =>
        log.error(s"getById() failed to find product {id: $id}")
        throw ApiException(StatusCodes.NotFound, s"Failed to find product with id: $id")
    }
  }

  def create(createProductDTO: CreateProductDTO): Future[Product] = {
    log.debug(s"create() was called {createProductDTO: ${createProductDTO.asJson.noSpaces}}")
    val product = createProductDTO.into[Product].withFieldConst(_.id, UUID.randomUUID().toString).transform
    productRepository.create(product).recover {
      case ex: MongoWriteException if ex.getCode == DUPLICATED_KEY_ERROR_CODE =>
        log.error(s"create() failed to create product due to duplicate key {ex: $ex, product: ${product.asJson.noSpaces}")
        throw ApiException(StatusCodes.Conflict, s"Failed to create, product with id: ${product.id} already exists")
      case ex: Exception =>
        log.error(s"create() failed to create product {ex: $ex, product: ${product.asJson.noSpaces}}")
        throw ApiException(StatusCodes.ServiceUnavailable, ex.getMessage)
    }
  }

  def delete(id: String): Future[Done] = {
    log.debug(s"delete() was called {id: $id}")
    productRepository.delete(id)
  }
}

