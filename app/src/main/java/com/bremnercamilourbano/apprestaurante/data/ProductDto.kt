package com.bremnercamilourbano.apprestaurante.data

import com.bremnercamilourbano.apprestaurante.model.Product

data class ProductDto(
    val id: Int? = null,
    val title: String? = null,
    val description: String? = null,
    val category: String? = null,
    val price: Double? = null,
    val imageUrl: String? = null,
    val available: Boolean? = null
)

data class ProductRequest(
    val title: String,
    val description: String,
    val category: String,
    val price: Double,
    val imageUrl: String?
)

data class AvailabilityRequest(val available: Boolean)

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val token: String)

data class OrderItemRequest(val productId: Int, val quantity: Int)
data class OrderRequest(
    val customerName: String,
    val note: String,
    val items: List<OrderItemRequest>
)
data class OrderResponse(val id: Int, val total: Double)

fun ProductDto.toDomain(): Product? {
    val productId = id ?: return null
    return Product(
        id = productId,
        title = title.orEmpty().ifBlank { "Producto $productId" },
        description = description.orEmpty(),
        category = category.orEmpty().ifBlank { "General" },
        price = price ?: 0.0,
        imageUrl = imageUrl,
        available = available ?: true
    )
}

fun Product.toRequest() = ProductRequest(
    title = title,
    description = description,
    category = category,
    price = price,
    imageUrl = imageUrl
)
