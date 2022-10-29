package com.hexagonkt.http.server.model.ws

import com.hexagonkt.http.model.HttpRequest
import com.hexagonkt.http.model.ws.WsSession

interface WsServerSession : WsSession {
    val httpRequest: HttpRequest
}
