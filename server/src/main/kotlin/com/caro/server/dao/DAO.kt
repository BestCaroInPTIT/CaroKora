package com.caro.server.dao

import java.sql.Connection

open class DAO {
    protected var con: Connection? = null

    protected fun <T> withConnection(block: (Connection) -> T): T {
        DatabaseConfig.getConnection().use { connection ->
            this.con = connection
            try {
                return block(connection)
            } finally {
                this.con = null
            }
        }
    }
}
