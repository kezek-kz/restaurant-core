package kezek.restaurant.core.domain.dto

import kezek.restaurant.core.domain.Category

case class CategoryListWithTotalDTO(total: Long, collection: Seq[Category])
