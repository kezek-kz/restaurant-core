package kezek.restaurant.core.repository.mongo

import akka.Done
import akka.http.scaladsl.model.StatusCodes
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.auto._
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import kezek.restaurant.core.codec.MainCodec
import kezek.restaurant.core.domain.ProductFilter._
import kezek.restaurant.core.domain.{Product, ProductFilter}
import kezek.restaurant.core.exception.ApiException
import kezek.restaurant.core.repository.ProductRepository
import kezek.restaurant.core.repository.mongo.ProductMongoRepository.fromFiltersToBson
import kezek.restaurant.core.util.{PaginationUtil, SortType}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Projections
import org.mongodb.scala.model.Sorts.{metaTextScore, orderBy}
import org.mongodb.scala.{Document, MongoClient, MongoCollection, MongoDatabase}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ProductMongoRepository {

  private def fromFiltersToBson(filters: Seq[ProductFilter]): Bson = {
    if (filters.isEmpty) Document()
    else and(
      filters.map {
        case ByTitleFilter(title) => equal("title", title)
        case ByDescriptionFilter(description) => equal("description", description)
        case ByCategorySlugFilter(categorySlug) => equal("categories", categorySlug)
        case ByCategorySlugListFilter(categorySlugList) => in("categories", categorySlugList)
        case other =>
          throw new RuntimeException(s"Failed to generate bson filter: $other not implemented")
      }: _*
    )
  }

}

class ProductMongoRepository()(implicit val mongoClient: MongoClient,
                                implicit val executionContext: ExecutionContext)
  extends ProductRepository with MainCodec with MongoRepository {

  override val sortingFields: Seq[String] = Seq("phoneNumber", "firstName")
  val config: Config = ConfigFactory.load()
  val database: MongoDatabase = mongoClient.getDatabase(config.getString("db.mongo.database"))
  val collection: MongoCollection[Document] = database.getCollection(config.getString("db.mongo.collection.product"))

  override def create(product: Product): Future[Product] = {
    collection.insertOne(toDocument(product)).head().map(_ => product)
  }

  private def toDocument(product: Product): Document = {
    Document(product.asJson.noSpaces)
  }

  override def update(id: String, product: Product): Future[Product] = {
    collection.replaceOne(equal("id", id), toDocument(product)).head().map { updateResult =>
      if (updateResult.wasAcknowledged()) {
        product
      } else {
        throw new RuntimeException(s"Failed to replace product with id: $id")
      }
    }
  }

  override def findById(id: String): Future[Option[Product]] = {
    collection
      .find(equal("id", id))
      .first()
      .headOption()
      .map {
        case Some(document) => Some(fromDocumentToProduct(document))
        case None => None
      }
  }

  private def fromDocumentToProduct(document: Document): Product = {
    parse(document.toJson()).toTry match {
      case Success(json) =>
        json.as[Product].toTry match {
          case Success(product) => product
          case Failure(exception) => throw exception
        }
      case Failure(exception) => throw exception
    }
  }

  override def paginate(filters: Seq[ProductFilter],
                        page: Option[Int],
                        pageSize: Option[Int],
                        sortParams: Map[String, SortType]): Future[Seq[Product]] = {
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
      .map(documents => documents map fromDocumentToProduct)
  }

  override def count(filters: Seq[ProductFilter]): Future[Long] = {
    collection.countDocuments(fromFiltersToBson(filters)).head()
  }

  override def delete(id: String): Future[Done] = {
    collection.deleteOne(equal("id", id)).head().map { deleteResult =>
      if (deleteResult.wasAcknowledged() && deleteResult.getDeletedCount == 1) {
        Done
      } else {
        throw ApiException(StatusCodes.NotFound, "Failed to delete product")
      }
    }
  }
}
