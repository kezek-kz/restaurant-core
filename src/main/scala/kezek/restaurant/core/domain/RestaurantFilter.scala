package kezek.restaurant.core.domain

trait RestaurantFilter

object RestaurantFilter {

  case class ByFirstNameFilter(firstName: String) extends RestaurantFilter
  case class ByLastNameFilter(lastName: String) extends RestaurantFilter
  case class ByEmailFilter(email: String) extends RestaurantFilter
  case class ByPhoneNumberFilter(phoneNumber: String) extends RestaurantFilter

}
