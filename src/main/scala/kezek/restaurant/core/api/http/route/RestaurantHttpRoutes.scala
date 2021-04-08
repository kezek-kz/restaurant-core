package kezek.restaurant.core.api.http.route

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, ExampleObject, Schema}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import kezek.restaurant.core.codec.MainCodec
import kezek.restaurant.core.domain.Restaurant
import kezek.restaurant.core.domain.dto.{CreateRestaurantDTO, RestaurantListWithTotalDTO, UpdateRestaurantDTO}
import kezek.restaurant.core.service.RestaurantService
import kezek.restaurant.core.util.{HttpUtil, SortUtil}
import org.joda.time.DateTime

import javax.ws.rs._
import scala.util.{Failure, Success}

trait RestaurantHttpRoutes extends MainCodec {

  val restaurantService: RestaurantService

  def restaurantHttpRoutes: Route = {
    pathPrefix("restaurants") {
      concat(
        updateRestaurant,
        getRestaurantById,
        deleteRestaurant,
        paginateRestaurants,
        createRestaurant
      )
    }
  }

  @GET
  @Operation(
    summary = "Get restaurant list",
    description = "Get filtered and paginated restaurant list",
    method = "GET",
    parameters = Array(
      new Parameter(name = "firstName", in = ParameterIn.QUERY, example = "Olzhas"),
      new Parameter(name = "lastName", in = ParameterIn.QUERY, example = "Dairov"),
      new Parameter(name = "email", in = ParameterIn.QUERY, example = "test@test.com"),
      new Parameter(name = "phoneNumber", in = ParameterIn.QUERY, example = "+77777777777"),
      new Parameter(name = "page", in = ParameterIn.QUERY, example = "1"),
      new Parameter(name = "pageSize", in = ParameterIn.QUERY, example = "10"),
      new Parameter(name = "sort", in = ParameterIn.QUERY, example = "+phoneNumber,-firstName")
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[RestaurantListWithTotalDTO]),
            mediaType = "application/json",
            examples = Array(new ExampleObject(name = "RestaurantListWithTotalDTO", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/restaurants")
  @Tag(name = "Restaurants")
  def paginateRestaurants: Route = {
    get {
      pathEndOrSingleSlash {
        parameters(
          "firstName".?,
          "lastName".?,
          "email".?,
          "phoneNumber".?,
          "page".as[Int].?,
          "pageSize".as[Int].?,
          "sort".?
        ) {
          (firstName,
           lastName,
           email,
           phoneNumber,
           page,
           pageSize,
           sort) => {
            onComplete {
              restaurantService.paginate(
                RestaurantService.generateFilters(
                  firstName = firstName,
                  lastName = lastName,
                  email = email,
                  phoneNumber = phoneNumber,
                ),
                page,
                pageSize,
                SortUtil.parseSortParams(sort)
              )
            } {
              case Success(result) => complete(result)
              case Failure(exception) => HttpUtil.completeThrowable(exception)
            }
          }
        }
      }
    }
  }

  @GET
  @Operation(
    summary = "Get restaurant by id",
    description = "Returns a full information about restaurant by id",
    method = "GET",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, example = "", required = true),
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[Restaurant]),
            examples = Array(new ExampleObject(name = "Restaurant", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/restaurants/{id}")
  @Tag(name = "Restaurants")
  def getRestaurantById: Route = {
    get {
      path(Segment) { id =>
        onComplete(restaurantService.getById(id)) {
          case Success(result) => complete(result)
          case Failure(exception) => HttpUtil.completeThrowable(exception)
        }
      }
    }
  }

  @POST
  @Operation(
    summary = "Create restaurant",
    description = "Creates new restaurant",
    method = "POST",
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[CreateRestaurantDTO]),
          mediaType = "application/json",
          examples = Array(
            new ExampleObject(name = "CreateRestaurantDTO", value = "")
          )
        )
      ),
      required = true
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[Restaurant]),
            examples = Array(new ExampleObject(name = "Restaurant", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/restaurants")
  @Tag(name = "Restaurants")
  def createRestaurant: Route = {
    post {
      pathEndOrSingleSlash {
        entity(as[CreateRestaurantDTO]) { body =>
          onComplete(restaurantService.create(body)) {
            case Success(result) => complete(result)
            case Failure(exception) => HttpUtil.completeThrowable(exception)
          }
        }
      }
    }
  }

  @PUT
  @Operation(
    summary = "Update restaurant",
    description = "Updates restaurant",
    method = "PUT",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, example = "", required = true),
    ),
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[UpdateRestaurantDTO]),
          mediaType = "application/json",
          examples = Array(new ExampleObject(name = "UpdateRestaurantDTO", value = ""))
        )
      ),
      required = true
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[Restaurant]),
            examples = Array(new ExampleObject(name = "Restaurant", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/restaurants/{id}")
  @Tag(name = "Restaurants")
  def updateRestaurant: Route = {
    put {
      path(Segment) { id =>
        entity(as[UpdateRestaurantDTO]) { body =>
          onComplete(restaurantService.update(id, body)) {
            case Success(result) => complete(result)
            case Failure(exception) => HttpUtil.completeThrowable(exception)
          }
        }
      }
    }
  }

  @DELETE
  @Operation(
    summary = "Deletes restaurant",
    description = "Deletes restaurant",
    method = "DELETE",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, example = "", required = true),
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "204",
        description = "OK",
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/restaurants/{id}")
  @Tag(name = "Restaurants")
  def deleteRestaurant: Route = {
    delete {
      path(Segment) { id =>
        onComplete(restaurantService.delete(id)) {
          case Success(_) => complete(StatusCodes.NoContent)
          case Failure(exception) => HttpUtil.completeThrowable(exception)
        }
      }
    }
  }

}
