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
import kezek.restaurant.core.domain.dto.{CreateProductDTO, ProductDTO, ProductListWithTotalDTO, UpdateProductDTO, UploadImageMultipartRequest}
import kezek.restaurant.core.service.ProductService
import kezek.restaurant.core.util.{HttpUtil, SortUtil}
import org.joda.time.DateTime

import javax.ws.rs._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

trait ProductHttpRoutes extends MainCodec {

  val productService: ProductService

  def productHttpRoutes: Route = {
    pathPrefix("products") {
      concat(
        uploadProductImage,
        deleteProductImage,
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
      new Parameter(name = "title", in = ParameterIn.QUERY, example = "ba9c3e3e-e593-49d7-b5e5-925cb8fb9b2a"),
      new Parameter(name = "description", in = ParameterIn.QUERY, example = "ba9c3e3e-e593-49d7-b5e5-925cb8fb9b2a"),
      new Parameter(name = "category", in = ParameterIn.QUERY, example = "ba9c3e3e-e593-49d7-b5e5-925cb8fb9b2a"),
      new Parameter(name = "categoryId", in = ParameterIn.QUERY, example = "ba9c3e3e-e593-49d7-b5e5-925cb8fb9b2a"),
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
          "categoryId".?,
          "title".?,
          "description".?,
          "page".as[Int].?,
          "pageSize".as[Int].?,
          "sort".?
        ) {
          (categoryId,
           title,
           description,
           page,
           pageSize,
           sort) => {
            onComplete {
              productService.paginate(
                ProductService.generateFilters(
                  categoryId = categoryId,
                  title = title,
                  description = description
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
            new ExampleObject(name = "CreateProductDTO", value = "{\n  \"title\": \"lime\",\n  \"slug\": \"lime\",\n  \"unit\": \"12 pc(s)\",\n  \"price\": 1.5,\n  \"salePrice\": 0,\n  \"discountInPercent\": 0,\n  \"description\": \"The lemon/lime, Citrus limon Osbeck, is a species of small evergreen tree in the flowering plant family Rutaceae, native to South Asia, primarily North eastern India.\",\n  \"type\": \"grocery\",\n  \"image\": \"https://res.cloudinary.com/redq-inc/image/upload/c_fit,q_auto:best,w_300/v1589614568/pickbazar/grocery/GreenLimes_jrodle.jpg\",\n  \"categories\": [\n\t\"id\": \"ba9c3e3e-e593-49d7-b5e5-925cb8fb9b2a\",\n  ]\n}")
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

  @POST
  @Operation(
    summary = "Upload product image",
    description = "Uploads product image to s3 and deletes old image",
    method = "POST",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, example = "227564ee-0896-47dc-970a-0d75e1caf71b")
    ),
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[UploadImageMultipartRequest]),
          mediaType = "multipart/form-data"
        )
      )
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[ProductDTO]),
            examples = Array(new ExampleObject(name = "ProductDTO", value = "")),
            mediaType = "application/json"
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/products/{id}/image")
  @Tag(name = "Products")
  def uploadProductImage: Route = {
    post {
      path(Segment / "image") { id =>
        withRequestTimeout(5.minutes) {
          fileUpload("image") {
            case (fileInfo, byteSource) => {
              onComplete(productService.uploadProductImage(byteSource, id, fileInfo)) {
                case Success(result) => complete(result)
                case Failure(exception) => HttpUtil.completeThrowable(exception)
              }
            }
          }
        }
      }
    }
  }

  @DELETE
  @Operation(
    summary = "Delete product image",
    description = "Deletes product image",
    method = "DELETE",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, example = "227564ee-0896-47dc-970a-0d75e1caf71b"),
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[ProductDTO]),
            examples = Array(new ExampleObject(name = "ProductDTO", value = "")),
            mediaType = "application/json"
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/products/{id}/image")
  @Tag(name = "Products")
  def deleteProductImage: Route = {
    delete {
      path(Segment / "image") { productId =>
        onComplete(productService.deleteProductImage(productId)) {
          case Success(result) => complete(result)
          case Failure(exception) => HttpUtil.completeThrowable(exception)
        }
      }
    }
  }

}
