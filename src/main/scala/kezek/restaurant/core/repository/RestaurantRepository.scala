package kezek.restaurant.core.repository

import akka.Done
import kezek.restaurant.core.domain.{Restaurant, RestaurantFilter}
import kezek.restaurant.core.util.SortType

import scala.concurrent.Future

trait RestaurantRepository {

  def create(restaurant: Restaurant): Future[Restaurant]

  def update(id: String, restaurant: Restaurant): Future[Restaurant]

  def findById(id: String): Future[Option[Restaurant]]

  def paginate(filters: Seq[RestaurantFilter],
               page: Option[Int],
               pageSize: Option[Int],
               sortParams: Map[String, SortType]): Future[Seq[Restaurant]]

  def count(filters: Seq[RestaurantFilter]): Future[Long]

  def delete(id: String): Future[Done]
}
