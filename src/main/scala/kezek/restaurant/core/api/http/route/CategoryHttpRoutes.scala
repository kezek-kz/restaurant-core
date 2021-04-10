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
import kezek.restaurant.core.domain.Category
import kezek.restaurant.core.domain.dto.{CategoryListWithTotalDTO, CreateCategoryDTO, UpdateCategoryDTO}
import kezek.restaurant.core.service.CategoryService
import kezek.restaurant.core.util.HttpUtil

import javax.ws.rs._
import scala.util.{Failure, Success}

trait CategoryHttpRoutes extends MainCodec {

  val categoryService: CategoryService

  def categoryHttpRoutes: Route = {
    pathPrefix("categories") {
      concat(
        updateCategory,
        getCategoryById,
        deleteCategory,
        paginateCategories,
        createCategory
      )
    }
  }

  @GET
  @Operation(
    summary = "Get category list",
    description = "Get filtered and paginated category list",
    method = "GET",
    parameters = Array(
      new Parameter(name = "title", in = ParameterIn.QUERY, example = "vegetables"),
      new Parameter(name = "page", in = ParameterIn.QUERY, example = "1"),
      new Parameter(name = "pageSize", in = ParameterIn.QUERY, example = "20"),
      new Parameter(name = "sort", in = ParameterIn.QUERY, example = "+title")
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "OK",
        content = Array(
          new Content(
            schema = new Schema(implementation = classOf[CategoryListWithTotalDTO]),
            mediaType = "application/json",
            examples = Array(new ExampleObject(name = "CategoryListWithTotalDTO", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/categories")
  @Tag(name = "Categories")
  def paginateCategories: Route = {
    get {
      pathEndOrSingleSlash {
        parameter("title".?) {
          title => {
            onComplete {
              categoryService.findAll(CategoryService.generateFilters(title = title))
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
    summary = "Get category by id",
    description = "Returns category by id",
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
            schema = new Schema(implementation = classOf[Category]),
            examples = Array(new ExampleObject(name = "Category", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/categories/{id}")
  @Tag(name = "Categories")
  def getCategoryById: Route = {
    get {
      path(Segment) { id =>
        onComplete(categoryService.getById(id)) {
          case Success(result) => complete(result)
          case Failure(exception) => HttpUtil.completeThrowable(exception)
        }
      }
    }
  }

  @POST
  @Operation(
    summary = "Create category",
    description = "Creates new category",
    method = "POST",
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[CreateCategoryDTO]),
          mediaType = "application/json",
          examples = Array(
            new ExampleObject(name = "CreateCategoryDTO", value = "{\n  \"title\": \"Fruits & Vegetables\",\n  \"slug\": \"fruits-and-vegetables\"\n}")
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
            schema = new Schema(implementation = classOf[Category]),
            examples = Array(new ExampleObject(name = "Category", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/categories")
  @Tag(name = "Categories")
  def createCategory: Route = {
    post {
      pathEndOrSingleSlash {
        entity(as[CreateCategoryDTO]) { body =>
          onComplete(categoryService.create(body)) {
            case Success(result) => complete(result)
            case Failure(exception) => HttpUtil.completeThrowable(exception)
          }
        }
      }
    }
  }

  @PUT
  @Operation(
    summary = "Update category",
    description = "Updates category",
    method = "PUT",
    parameters = Array(
      new Parameter(name = "id", in = ParameterIn.PATH, example = "", required = true),
    ),
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[UpdateCategoryDTO]),
          mediaType = "application/json",
          examples = Array(new ExampleObject(name = "UpdateCategoryDTO", value = "{\n  \"title\": \"Fruits & Vegetables\",\n  \"slug\": \"fruits-and-vegetables\"\n}"))
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
            schema = new Schema(implementation = classOf[Category]),
            examples = Array(new ExampleObject(name = "Category", value = ""))
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/categories/{id}")
  @Tag(name = "Categories")
  def updateCategory: Route = {
    put {
      path(Segment) { id =>
        entity(as[UpdateCategoryDTO]) { body =>
          onComplete(categoryService.update(id, body)) {
            case Success(result) => complete(result)
            case Failure(exception) => HttpUtil.completeThrowable(exception)
          }
        }
      }
    }
  }

  @DELETE
  @Operation(
    summary = "Delete category",
    description = "Deletes category",
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
  @Path("/categories/{id}")
  @Tag(name = "Categories")
  def deleteCategory: Route = {
    delete {
      path(Segment) { id =>
        onComplete(categoryService.delete(id)) {
          case Success(_) => complete(StatusCodes.NoContent)
          case Failure(exception) => HttpUtil.completeThrowable(exception)
        }
      }
    }
  }

}
