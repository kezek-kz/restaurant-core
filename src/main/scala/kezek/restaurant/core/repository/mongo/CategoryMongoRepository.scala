package kezek.restaurant.core.repository.mongo

import akka.Done
import akka.http.scaladsl.model.StatusCodes
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.auto._
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import kezek.restaurant.core.codec.MainCodec
import kezek.restaurant.core.domain.CategoryFilter._
import kezek.restaurant.core.domain.{Category, CategoryFilter}
import kezek.restaurant.core.exception.ApiException
import kezek.restaurant.core.repository.CategoryRepository
import kezek.restaurant.core.repository.mongo.CategoryMongoRepository.fromFiltersToBson
import kezek.restaurant.core.util.{PaginationUtil, SortType}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.{Document, MongoClient, MongoCollection, MongoDatabase}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object CategoryMongoRepository {

  private def fromFiltersToBson(filters: Seq[CategoryFilter]): Bson = {
    if (filters.isEmpty) Document()
    else and(
      filters.map {
        case ByTitleFilter(title) => text(title)
        case ByMultipleIdsFilter(ids) => in("id", ids: _*)
        case other =>
          throw new RuntimeException(s"Failed to generate bson filter: $other not implemented")
      }: _*
    )
  }

}

class CategoryMongoRepository()(implicit val mongoClient: MongoClient,
                                implicit val executionContext: ExecutionContext)
  extends CategoryRepository with MainCodec with MongoRepository {

  override val sortingFields: Seq[String] = Seq("title")
  val config: Config = ConfigFactory.load()
  val database: MongoDatabase = mongoClient.getDatabase(config.getString("db.mongo.database"))
  val collection: MongoCollection[Document] = database.getCollection(config.getString("db.mongo.collection.category"))

  override def create(category: Category): Future[Category] = {
    collection.insertOne(toDocument(category)).head().map(_ => category)
  }

  override def update(id: String, category: Category): Future[Category] = {
    collection.replaceOne(equal("id", id), toDocument(category)).head().map { updateResult =>
      if (updateResult.wasAcknowledged()) {
        if(updateResult.getMatchedCount == 1) {
          category
        } else {
          throw ApiException(StatusCodes.NotFound, s"Failed to find category with id: $id")
        }
      } else {
        throw new RuntimeException(s"Failed to replace category with id: $id")
      }
    }
  }

  private def toDocument(category: Category): Document = {
    Document(category.asJson.noSpaces)
  }

  override def findById(id: String): Future[Option[Category]] = {
    collection
      .find(equal("id", id))
      .first()
      .headOption()
      .map {
        case Some(document) => Some(fromDocumentToCategory(document))
        case None => None
      }
  }

  override def paginate(filters: Seq[CategoryFilter],
                        page: Option[Int],
                        pageSize: Option[Int],
                        sortParams: Map[String, SortType]): Future[Seq[Category]] = {
    val filtersBson = fromFiltersToBson(filters)
    val sortBson = fromSortParamsToBson(sortParams)
    val limit = pageSize.getOrElse(10)
    val offset = PaginationUtil.offset(page = page.getOrElse(1), size = limit)

    collection
      .find(filtersBson)
      .sort(sortBson)
      .skip(offset)
      .limit(limit)
      .toFuture()
      .map(documents => documents map fromDocumentToCategory)
  }

  override def findAll(filters: Seq[CategoryFilter],
                       sortParams: Map[String, SortType]): Future[Seq[Category]] = {
    val filtersBson = fromFiltersToBson(filters)
    val sortBson = fromSortParamsToBson(sortParams)

    collection
      .find(filtersBson)
      .sort(sortBson)
      .toFuture()
      .map(documents => documents map fromDocumentToCategory)
  }

  private def fromDocumentToCategory(document: Document): Category = {
    parse(document.toJson()).toTry match {
      case Success(json) =>
        json.as[Category].toTry match {
          case Success(category) => category
          case Failure(exception) => throw exception
        }
      case Failure(exception) => throw exception
    }
  }

  override def count(filters: Seq[CategoryFilter]): Future[Long] = {
    collection.countDocuments(fromFiltersToBson(filters)).head()
  }

  override def delete(id: String): Future[Done] = {
    collection.deleteOne(equal("id", id)).head().map { deleteResult =>
      if (deleteResult.wasAcknowledged() && deleteResult.getDeletedCount == 1) {
        Done
      } else {
        throw ApiException(StatusCodes.NotFound, "Failed to delete category")
      }
    }
  }
}
