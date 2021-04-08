package kezek.restaurant.core.service

import akka.Done
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import kezek.restaurant.core.codec.MainCodec
import kezek.restaurant.core.domain.RestaurantFilter._
import kezek.restaurant.core.domain._
import kezek.restaurant.core.domain.dto.{CreateRestaurantDTO, RestaurantListWithTotalDTO, UpdateRestaurantDTO}
import kezek.restaurant.core.exception.ApiException
import kezek.restaurant.core.repository.RestaurantRepository
import kezek.restaurant.core.repository.mongo.RestaurantMongoRepository
import kezek.restaurant.core.repository.mongo.MongoRepository.DUPLICATED_KEY_ERROR_CODE
import kezek.restaurant.core.util.SortType
import org.mongodb.scala.{MongoClient, MongoWriteException}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

object RestaurantService extends MainCodec {

  def generateFilters(firstName: Option[String],
                      lastName: Option[String],
                      email: Option[String],
                      phoneNumber: Option[String]): Seq[RestaurantFilter] = {
    var filters: Seq[RestaurantFilter] = Seq.empty
    if (firstName.isDefined) filters = filters :+ ByFirstNameFilter(firstName.get)
    if (lastName.isDefined) filters = filters :+ ByLastNameFilter(lastName.get)
    if (email.isDefined) filters = filters :+ ByEmailFilter(email.get)
    if (phoneNumber.isDefined) filters = filters :+ ByPhoneNumberFilter(phoneNumber.get)
    filters
  }

}

class RestaurantService()(implicit val mongoClient: MongoClient,
                        implicit val executionContext: ExecutionContext,
                        implicit val system: ActorSystem[_]) extends MainCodec {

  val config: Config = ConfigFactory.load()
  val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)
  val restaurantRepository: RestaurantRepository = new RestaurantMongoRepository()

  def paginate(filters: Seq[RestaurantFilter],
               page: Option[Int],
               pageSize: Option[Int],
               sortParams: Map[String, SortType]): Future[RestaurantListWithTotalDTO] = {
    log.debug(s"paginate() was called {filters: $filters, page: $page, pageSize: $pageSize, sortParams: $sortParams}")
    (for (
      restaurants <- restaurantRepository.paginate(filters, page, pageSize, sortParams);
      count <- restaurantRepository.count(filters)
    ) yield RestaurantListWithTotalDTO(
      collection = restaurants,
      total = count
    )).recover { exception =>
      log.error(s"paginate() failed to paginate restaurants {exception: $exception, filters: $filters, page: $page, pageSize: $pageSize, sortParams: $sortParams}")
      throw new RuntimeException(s"Failed to paginate restaurants: $exception")
    }
  }


  def update(id: String, updateRestaurantDTO: UpdateRestaurantDTO): Future[Restaurant] = {
    log.debug(s"update() was called {id: $id, updateRestaurantDTO: $updateRestaurantDTO}")
    val restaurant = Restaurant(
      id,
      updateRestaurantDTO.firstName,
      updateRestaurantDTO.lastName,
      updateRestaurantDTO.email,
      updateRestaurantDTO.phoneNumber
    )
    restaurantRepository.update(id, restaurant)
  }

  def getById(id: String): Future[Restaurant] = {
    log.debug(s"getById() was called {id: $id}")
    restaurantRepository.findById(id).map {
      case Some(restaurant) => restaurant
      case None =>
        log.error(s"getById() failed to find restaurant {id: $id}")
        throw ApiException(StatusCodes.NotFound, s"Failed to find restaurant with id: $id")
    }
  }

  def create(createRestaurantDTO: CreateRestaurantDTO): Future[Restaurant] = {
    log.debug(s"create() was called {createRestaurantDTO: ${createRestaurantDTO.asJson.noSpaces}}")
    val restaurant = Restaurant(
      UUID.randomUUID().toString,
      createRestaurantDTO.firstName,
      createRestaurantDTO.lastName,
      createRestaurantDTO.email,
      createRestaurantDTO.phoneNumber
    )
    restaurantRepository.create(restaurant).recover {
      case ex: MongoWriteException if ex.getCode == DUPLICATED_KEY_ERROR_CODE =>
        log.error(s"create() failed to create restaurant due to duplicate key {ex: $ex, restaurant: ${restaurant.asJson.noSpaces}")
        throw ApiException(StatusCodes.Conflict, s"Failed to create, restaurant with id: ${restaurant.id} already exists")
      case ex: Exception =>
        log.error(s"create() failed to create restaurant {ex: $ex, restaurant: ${restaurant.asJson.noSpaces}}")
        throw ApiException(StatusCodes.ServiceUnavailable, ex.getMessage)
    }
  }

  def delete(id: String): Future[Done] = {
    log.debug(s"delete() was called {id: $id}")
    restaurantRepository.delete(id)
  }
}

