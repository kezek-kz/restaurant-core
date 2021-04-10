package kezek.restaurant.core.scripts

import com.typesafe.config.{Config, ConfigFactory}
import org.mongodb.scala.model.IndexOptions
import org.mongodb.scala.model.Indexes.{ascending, text}
import org.mongodb.scala.{Document, MongoClient, MongoCollection, MongoDatabase}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object SeedScript {

  val config: Config = ConfigFactory.load()
  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  def createProductCollectionIndexes()(implicit mongoClient: MongoClient,
                                       executionContext: ExecutionContext): Unit = {
    log.debug(s"createProductCollectionIndexes() was called")
    val database: MongoDatabase = mongoClient.getDatabase(config.getString("db.mongo.database"))
    val collection: MongoCollection[Document] = database.getCollection(config.getString("db.mongo.collection.product"))

    collection.createIndex(
      ascending("id"),
      IndexOptions().unique(true)
    ).toFuture().onComplete {
      case Success(_) =>
        log.debug("createProductCollectionIndexes() successfully created unique indexes for id")
      case Failure(exception) =>
        log.error(s"createProductCollectionIndexes() failed to create unique indexes for id{details: $exception}")
    }

    collection.createIndex(
      ascending("slug"),
      IndexOptions().unique(true)
    ).toFuture().onComplete {
      case Success(_) =>
        log.debug("createProductCollectionIndexes() successfully created unique indexes for slug ")
      case Failure(exception) =>
        log.error(s"createProductCollectionIndexes() failed to create unique indexes for slug {details: $exception}")
    }

//    collection.createIndex(
//      text("title")
//    ).toFuture().onComplete {
//      case Success(_) =>
//        log.debug("createProductCollectionIndexes() successfully created wildcard index")
//      case Failure(exception) =>
//        log.error(s"createProductCollectionIndexes() failed to create  wildcard index {details: $exception}")
//    }
  }

  def createCategoryCollectionIndexes()(implicit mongoClient: MongoClient,
                                       executionContext: ExecutionContext): Unit = {
    log.debug(s"createCategoryCollectionIndexes() was called")
    val database: MongoDatabase = mongoClient.getDatabase(config.getString("db.mongo.database"))
    val collection: MongoCollection[Document] = database.getCollection(config.getString("db.mongo.collection.category"))

    collection.createIndex(
      ascending("id"),
      IndexOptions().unique(true)
    ).toFuture().onComplete {
      case Success(_) =>
        log.debug("createCategoryCollectionIndexes() successfully created unique indexes for id")
      case Failure(exception) =>
        log.error(s"createCategoryCollectionIndexes() failed to create unique indexes for id{details: $exception}")
    }

    collection.createIndex(
      ascending("slug"),
      IndexOptions().unique(true)
    ).toFuture().onComplete {
      case Success(_) =>
        log.debug("createCategoryCollectionIndexes() successfully created unique indexes for slug ")
      case Failure(exception) =>
        log.error(s"createCategoryCollectionIndexes() failed to create unique indexes for slug {details: $exception}")
    }

    collection.createIndex(
      text("title")
    ).toFuture().onComplete {
      case Success(_) =>
        log.debug("createCategoryCollectionIndexes() successfully created wildcard index")
      case Failure(exception) =>
        log.error(s"createCategoryCollectionIndexes() failed to create  wildcard index {details: $exception}")
    }
  }

}
