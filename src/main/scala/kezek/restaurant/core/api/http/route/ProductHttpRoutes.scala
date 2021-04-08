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
import kezek.restaurant.core.domain.Product
import kezek.restaurant.core.domain.dto.{CreateProductDTO, ProductListWithTotalDTO, UpdateProductDTO}
import kezek.restaurant.core.service.ProductService
import kezek.restaurant.core.util.{HttpUtil, SortUtil}
import org.joda.time.DateTime

import javax.ws.rs._
import scala.util.{Failure, Success}

trait ProductHttpRoutes extends MainCodec {

  val productService: ProductService

  def productHttpRoutes: Route = {
    pathPrefix("products") {
      concat(
        updateProduct,
        getProductById,
        deleteProduct,
        paginateProducts,
        createProduct
      )
    }
  }

  @GET
  @Operation(
    summary = "Get product list",
    description = "Get filtered and paginated product list",
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
            schema = new Schema(implementation = classOf[ProductListWithTotalDTO]),
            mediaType = "application/json",
            examples = Array(new ExampleObject(name = "ProductListWithTotalDTO", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/products")
  @Tag(name = "Products")
  def paginateProducts: Route = {
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
              productService.paginate(
                ProductService.generateFilters(
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
    summary = "Get product by id",
    description = "Returns a full information about product by id",
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
            schema = new Schema(implementation = classOf[Product]),
            examples = Array(new ExampleObject(name = "Product", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/products/{id}")
  @Tag(name = "Products")
  def getProductById: Route = {
    get {
      path(Segment) { id =>
        onComplete(productService.getById(id)) {
          case Success(result) => complete(result)
          case Failure(exception) => HttpUtil.completeThrowable(exception)
        }
      }
    }
  }

  @POST
  @Operation(
    summary = "Create product",
    description = "Creates new product",
    method = "POST",
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[CreateProductDTO]),
          mediaType = "application/json",
          examples = Array(
            new ExampleObject(name = "CreateProductDTO", value = "")
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
            schema = new Schema(implementation = classOf[Product]),
            examples = Array(new ExampleObject(name = "Product", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/products")
  @Tag(name = "Products")
  def createProduct: Route = {
    post {
      pathEndOrSingleSlash {
        entity(as[CreateProductDTO]) { body =>
          onComplete(productService.create(body)) {
            case Success(result) => complete(result)
            case Failure(exception) => HttpUtil.completeThrowable(exception)
          }
        }
      }
    }
  }

  @PUT
  @Operation(
    summary = "Update product",
    description = "Updates product",
    method = "PUT",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, example = "", required = true),
    ),
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[UpdateProductDTO]),
          mediaType = "application/json",
          examples = Array(new ExampleObject(name = "UpdateProductDTO", value = ""))
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
            schema = new Schema(implementation = classOf[Product]),
            examples = Array(new ExampleObject(name = "Product", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/products/{id}")
  @Tag(name = "Products")
  def updateProduct: Route = {
    put {
      path(Segment) { id =>
        entity(as[UpdateProductDTO]) { body =>
          onComplete(productService.update(id, body)) {
            case Success(result) => complete(result)
            case Failure(exception) => HttpUtil.completeThrowable(exception)
          }
        }
      }
    }
  }

  @DELETE
  @Operation(
    summary = "Deletes product",
    description = "Deletes product",
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
  @Path("/products/{id}")
  @Tag(name = "Products")
  def deleteProduct: Route = {
    delete {
      path(Segment) { id =>
        onComplete(productService.delete(id)) {
          case Success(_) => complete(StatusCodes.NoContent)
          case Failure(exception) => HttpUtil.completeThrowable(exception)
        }
      }
    }
  }

}
