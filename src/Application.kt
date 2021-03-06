package io.sebi

import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.html.*
import kotlinx.html.*
import io.ktor.http.content.*
import io.ktor.sessions.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

val protocol = System.getenv("PROTOCOL") ?: "http"

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(Sessions) {
        cookie<CodeedSession>("WHOAMI_SESSION", SessionStorageMemory()) {
            cookie.path="/"
            cookie.extensions["SameSite"] = "lax"
        }
    }

    val client = HttpClient(Apache)
    routing {
        get("/") {
            val sess = call.sessions.get<CodeedSession>()
            if(sess != null) {
                call.respondRedirect("/me")
            }
            val cburl = client.get<String>("https://stscodeed.azurewebsites.net/GET/ad/login?cb=$protocol://${call.request.host()}:${if(protocol != "https") call.request.port().toString() else ""}/callback")

            call.respondHtml {
                body {
                    h1 {
                        +"Wondering who you are?"
                    }
                    p {
                        a {
                            href = cburl
                            +"Find out by logging in."
                        }
                    }
                }
            }
        }

        get("/callback") {
            val tok = call.request.queryParameters["token"] ?: error("Wrong.")
            call.sessions.set(CodeedSession(tok))
            call.respondRedirect("/me")
        }

        get("/me") {
            val tok = call.sessions.get<CodeedSession>()?.token
            if(tok == null) {
                call.respondRedirect("/")
            }
            val contents = client.get<String>("https://stscodeed.azurewebsites.net/GET/ad/profile?token=$tok")
            val usr = Gson().fromJson(contents, User::class.java)
            call.respondHtml {
                body {
                    h1 {
                        +"Hello, ${usr.displayName}!"
                    }
                    p {
                        +"Your ID is ${usr.id}."
                    }
                    p {
                        +"Your Principal Name is ${usr.userPrincipalName}."
                    }
                    p {
                        a {
                            href="/logout"
                            +"Log me out! 😤"
                        }
                    }
                }
            }
        }

        get("/logout") {
            call.sessions.clear<CodeedSession>()
            call.respondRedirect("/")
        }

        static("/static") {
            resources("static")
        }
    }
}

data class CodeedSession(val token: String)

data class User(
    val displayName: String,
    val givenName: String,
    val surname: String,
    val userPrincipalName: String,
    val id: String
)