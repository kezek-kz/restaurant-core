package kezek.restaurant.core.repository.mongo

import akka.Done
import akka.http.scaladsl.model.StatusCodes
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.auto._
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import kezek.restaurant.core.codec.MainCodec
import kezek.restaurant.core.domain.RestaurantFilter._
import kezek.restaurant.core.domain.{Restaurant, RestaurantFilter}
import kezek.restaurant.core.exception.ApiException
import kezek.restaurant.core.repository.RestaurantRepository
import kezek.restaurant.core.repository.mongo.RestaurantMongoRepository.fromFiltersToBson
import kezek.restaurant.core.util.{PaginationUtil, SortType}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Projections
import org.mongodb.scala.model.Sorts.{metaTextScore, orderBy}
import org.mongodb.scala.{Document, MongoClient, MongoCollection, MongoDatabase}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object RestaurantMongoRepository {

  private def fromFiltersToBson(filters: Seq[RestaurantFilter]): Bson = {
    if (filters.isEmpty) Document()
    else and(
      filters.map {
        case ByFirstNameFilter(firstName) => equal("firstName", firstName)
        case ByLastNameFilter(lastName) => equal("lastName", lastName)
        case ByEmailFilter(email) => equal("email", email)
        case ByPhoneNumberFilter(phoneNumber) => equal("phoneNumber", phoneNumber)
        case other =>
          throw new RuntimeException(s"Failed to generate bson filter: $other not implemented")
      }: _*
    )
  }

}

class RestaurantMongoRepository()(implicit val mongoClient: MongoClient,
                                implicit val executionContext: ExecutionContext)
  extends RestaurantRepository with MainCodec with MongoRepository {

  override val sortingFields: Seq[String] = Seq("phoneNumber", "firstName")
  val config: Config = ConfigFactory.load()
  val database: MongoDatabase = mongoClient.getDatabase(config.getString("db.mongo.database"))
  val collection: MongoCollection[Document] = database.getCollection(config.getString("db.mongo.collection.restaurant"))

  override def create(restaurant: Restaurant): Future[Restaurant] = {
    collection.insertOne(toDocument(restaurant)).head().map(_ => restaurant)
  }

  private def toDocument(restaurant: Restaurant): Document = {
    Document(restaurant.asJson.noSpaces)
  }

  override def update(id: String, restaurant: Restaurant): Future[Restaurant] = {
    collection.replaceOne(equal("id", id), toDocument(restaurant)).head().map { updateResult =>
      if (updateResult.wasAcknowledged()) {
        restaurant
      } else {
        throw new RuntimeException(s"Failed to replace restaurant with id: $id")
      }
    }
  }

  override def findById(id: String): Future[Option[Restaurant]] = {
    collection
      .find(equal("id", id))
      .first()
      .headOption()
      .map {
        case Some(document) => Some(fromDocumentToRestaurant(document))
        case None => None
      }
  }

  private def fromDocumentToRestaurant(document: Document): Restaurant = {
    parse(document.toJson()).toTry match {
      case Success(json) =>
        json.as[Restaurant].toTry match {
          case Success(restaurant) => restaurant
          case Failure(exception) => throw exception
        }
      case Failure(exception) => throw exception
    }
  }

  override def paginate(filters: Seq[RestaurantFilter],
                        page: Option[Int],
                        pageSize: Option[Int],
                        sortParams: Map[String, SortType]): Future[Seq[Restaurant]] = {
    val filtersBson = fromFiltersToBson(filters)
    val sortBson = orderBy(fromSortParamsToBson(sortParams), metaTextScore("score"))
    val limit = pageSize.getOrElse(10)
    val offset = PaginationUtil.offset(page = page.getOrElse(1), size = limit)

    collection
      .find(filtersBson)
      .projection(Projections.metaTextScore("score"))
      .sort(sortBson)
      .skip(offset)
      .limit(limit)
      .toFuture()
      .map(documents => documents map fromDocumentToRestaurant)
  }

  override def count(filters: Seq[RestaurantFilter]): Future[Long] = {
    collection.countDocuments(fromFiltersToBson(filters)).head()
  }

  override def delete(id: String): Future[Done] = {
    collection.deleteOne(equal("id", id)).head().map { deleteResult =>
      if (deleteResult.wasAcknowledged() && deleteResult.getDeletedCount == 1) {
        Done
      } else {
        throw ApiException(StatusCodes.NotFound, "Failed to delete restaurant")
      }
    }
  }
}
