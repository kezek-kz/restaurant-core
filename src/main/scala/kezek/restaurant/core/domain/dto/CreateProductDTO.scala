package kezek.restaurant.core.domain.dto

case class CreateProductDTO(title: String,
                            slug: String,
                            unit: String,
                            price: BigDecimal,
                            salePrice: BigDecimal,
                            discountInPercent: Int,
                            description: String,
                            `type`: String,
                            image: String,
                            categories: Seq[String])
