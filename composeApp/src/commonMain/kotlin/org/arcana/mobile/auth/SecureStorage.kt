package org.arcana.mobile.auth

expect class SecureStorage() {
    fun save(key: String, value: String)
    fun load(key: String): String?
    fun delete(key: String)
}
