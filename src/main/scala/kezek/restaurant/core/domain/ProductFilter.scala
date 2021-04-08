package kezek.restaurant.core.domain

trait ProductFilter

object ProductFilter {

  case class ByFirstNameFilter(firstName: String) extends ProductFilter
  case class ByLastNameFilter(lastName: String) extends ProductFilter
  case class ByEmailFilter(email: String) extends ProductFilter
  case class ByPhoneNumberFilter(phoneNumber: String) extends ProductFilter

}
