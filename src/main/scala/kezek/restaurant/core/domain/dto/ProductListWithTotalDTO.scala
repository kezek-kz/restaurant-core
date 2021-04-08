package kezek.restaurant.core.domain.dto

import kezek.restaurant.core.domain.Product

case class ProductListWithTotalDTO(total: Long, collection: Seq[Product])
