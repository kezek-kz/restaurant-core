package kezek.restaurant.core.domain

trait CategoryFilter

object CategoryFilter {

  case class ByTitleFilter(title: String) extends CategoryFilter
  case class ByMultipleIdsFilter(ids: Seq[String]) extends CategoryFilter

}


