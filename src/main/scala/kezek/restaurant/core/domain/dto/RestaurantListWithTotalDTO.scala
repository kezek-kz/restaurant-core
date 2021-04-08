package kezek.restaurant.core.domain.dto

import kezek.restaurant.core.domain.Restaurant

case class RestaurantListWithTotalDTO(total: Long, collection: Seq[Restaurant])
