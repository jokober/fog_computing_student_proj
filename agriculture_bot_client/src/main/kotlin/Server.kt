import Database.getRandomOpenMissions
import Database.reopenExpiredMissions
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import kotlin.random.Random.Default.nextInt

fun main() {
    Server().run()
}

@Serializable
data class ServerResponse(val missionList: List<Mission>)

class Server {
    private lateinit var socket: ZMQ.Socket

    fun run() {
        ZContext().use { context ->
            socket = context.createSocket(SocketType.REP)
            socket.bind("tcp://*:5555")
            println("Server ready")

            //Create demo data which is inserted into the database
            createMissionDemoDataAndInsertIntoDB(10)

            while (!Thread.currentThread().isInterrupted) {
                processClientRequest(
                    Json.decodeFromJsonElement<ClientRequest>(
                        Json.parseToJsonElement(String(socket.recv(0), ZMQ.CHARSET)).jsonObject
                    )
                )

                sendMission()
                reopenExpiredMissions()
                Thread.sleep(730)
            }
        }
    }

    private fun processClientRequest(clientRequest: ClientRequest) {
        clientRequest.missionResultsList.forEach {
            when (it) {
                is InspectionMission -> processInspectionResultData(it)
                is WateringMission -> processWateringResultData(it)
            }
        }
    }


    private fun sendMission() {
        println("sending...")
        val serverResponse = ServerResponse(getRandomOpenMissions(nextInt(4)))
        val response = Json.encodeToString<ServerResponse>(serverResponse)
        socket.send(response.toByteArray(ZMQ.CHARSET), 0)
        println("Send: $response")

        Database.updateExpirationDate(serverResponse.missionList)
    }

    private fun processInspectionResultData(mission: InspectionMission) {
        print(" Server is processing inspection results ...")
        println(mission)
        Database.updateMission(mission)
    }

    private fun processWateringResultData(mission: WateringMission) {
        print(" Server is processing watering results ...")
        Database.updateMission(mission)
    }
}