package kezek.restaurant.core.service

import akka.Done
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.scalaland.chimney.dsl.TransformerOps
import kezek.restaurant.core.codec.MainCodec
import kezek.restaurant.core.domain.CategoryFilter._
import kezek.restaurant.core.domain.ProductFilter.ByCategoryIdFilter
import kezek.restaurant.core.domain._
import kezek.restaurant.core.domain.dto.{CategoryListWithTotalDTO, CreateCategoryDTO, UpdateCategoryDTO}
import kezek.restaurant.core.exception.ApiException
import kezek.restaurant.core.repository.{CategoryRepository, ProductRepository}
import kezek.restaurant.core.repository.mongo.{CategoryMongoRepository, ProductMongoRepository}
import kezek.restaurant.core.repository.mongo.MongoRepository.DUPLICATED_KEY_ERROR_CODE
import kezek.restaurant.core.util.{ASC, SortType}
import org.mongodb.scala.{MongoClient, MongoWriteException}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

object CategoryService extends MainCodec {

  def generateFilters(title: Option[String]): Seq[CategoryFilter] = {
    var filters: Seq[CategoryFilter] = Seq.empty
    if (title.isDefined) filters = filters :+ ByTitleFilter(title.get)
    filters
  }

}

class CategoryService()(implicit val mongoClient: MongoClient,
                       implicit val executionContext: ExecutionContext,
                       implicit val system: ActorSystem[_]) extends MainCodec {

  val config: Config = ConfigFactory.load()
  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)
  val categoryRepository: CategoryRepository = new CategoryMongoRepository()
  val productRepository: ProductRepository = new ProductMongoRepository()

  def findAll(filters: Seq[CategoryFilter]): Future[Seq[Category]] = {
    log.debug(s"paginate() was called {filters: $filters}")
    categoryRepository.findAll(filters, Map("title" -> ASC))
  }

  def update(id: String, updateCategoryDTO: UpdateCategoryDTO): Future[Category] = {
    log.debug(s"update() was called {id: $id, updateCategoryDTO: $updateCategoryDTO}")
    val category = updateCategoryDTO.into[Category].withFieldConst(_.id, id).transform
    categoryRepository.update(id, category).recover {
      case ex: MongoWriteException if ex.getCode == DUPLICATED_KEY_ERROR_CODE =>
        log.error(s"update() failed to update category due to duplicate key {ex: $ex, category: ${category.asJson.noSpaces}")
        throw ApiException(StatusCodes.Conflict, s"Failed to update, category with slug '${category.slug}' already exists")
      case ex: Exception =>
        log.error(s"update() failed to update category {ex: $ex, category: ${category.asJson.noSpaces}}")
        throw ApiException(StatusCodes.ServiceUnavailable, ex.getMessage)
    }
  }

  def getById(id: String): Future[Category] = {
    log.debug(s"getById() was called {id: $id}")
    categoryRepository.findById(id).map {
      case Some(category) => category
      case None =>
        log.error(s"getById() failed to find category {id: $id}")
        throw ApiException(StatusCodes.NotFound, s"Failed to find category with id: $id")
    }
  }

  def create(createCategoryDTO: CreateCategoryDTO): Future[Category] = {
    log.debug(s"create() was called {createCategoryDTO: ${createCategoryDTO.asJson.noSpaces}}")
    val category = createCategoryDTO.into[Category]
      .withFieldConst(_.id, UUID.randomUUID().toString)
      .transform
    categoryRepository.create(category).recover {
      case ex: MongoWriteException if ex.getCode == DUPLICATED_KEY_ERROR_CODE =>
        log.error(s"create() failed to create category due to duplicate key {ex: $ex, category: ${category.asJson.noSpaces}")
        throw ApiException(StatusCodes.Conflict, s"Failed to create, category with slug '${category.slug}' already exists")
      case ex: Exception =>
        log.error(s"create() failed to create category {ex: $ex, category: ${category.asJson.noSpaces}}")
        throw ApiException(StatusCodes.ServiceUnavailable, ex.getMessage)
    }
  }

  def delete(id: String): Future[Unit] = {
    log.debug(s"delete() was called {id: $id}")

    productRepository.count(Seq(ByCategoryIdFilter(id))).map { count =>
      if(count == 0){
        categoryRepository.delete(id)
      } else {
        log.warn(s"delete() failed due to existence of products with category to delete {id: $id, count: $count}")
        throw ApiException(StatusCodes.Conflict, s"Failed to delete category with id: $id, there are/is $count product(s) assigned to it")
      }
    }
  }
}

