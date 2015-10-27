import java.security.MessageDigest
import scala.util.Random
import akka.actor.{ActorRef, Actor, Props, ActorSystem}
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import java.util.UUID
import scala.util.Random
import scala.collection.mutable.ArrayBuffer
import akka.routing.RoundRobinRouter
import com.typesafe.config.ConfigFactory
import java.net.InetAddress


object Project_Runner  {
  case class BeginMining_Server()
  case class Start_Mining()
  case class WorkFinished_WorkerStopped()
  case class BeginMining_Client(nrOfZeroes: Int, startRange: Int, endRange: Int) 
  case class ConnectToServer()   
  case class GiveClientWork(nrOfWorkers_client: Int)  
  case class Bitcoin_Found(coin: String)
  case class Result(nrOfBitcoins: Int, duration: Duration)
  case class Client_shutdown()

  def main(args: Array[String]) {
  	
      val serverConfig = ConfigFactory.parseString(
        """ 
        akka{ 
          actor{ 
            provider = "akka.remote.RemoteActorRefProvider" 
          } 
          remote{ 
                  enabled-transports = ["akka.remote.netty.tcp"] 
              netty.tcp{ 
			hostname = "127.0.0.1"
			port = 3000            
            
            
          } 
        }      
      }""")

      val clientConfig = ConfigFactory.parseString(
        """akka{
            actor{
              provider = "akka.remote.RemoteActorRefProvider"
            }
            remote{
                     enabled-transports = ["akka.remote.netty.tcp"]
              netty.tcp{
              
              port = 0
            }
          }     
        }""")
      








    class Server_Master(acsys: ActorSystem, nrOfZeroes: Int, listener: ActorRef)  extends Actor {
      val nrOfWorkers_server:Int = Runtime.getRuntime().availableProcessors()
      var nrOfBitcoins: Int = _
      
      var work_unit_index: Int = _
      val start_time: Long = System.currentTimeMillis
      val interval = 10000000
      val server_actorsList: ArrayBuffer[ActorRef] = new ArrayBuffer[ActorRef]
      var server_activeWorkers: Int = nrOfWorkers_server
      var nrOf_activeClients: Int = 0
      val temp1: Boolean = false
      val temp2: Boolean = false
      
      def receive = {
        case GiveClientWork(nrOfWorkers_client) =>
          nrOf_activeClients+=1
          sender ! BeginMining_Client(nrOfZeroes, work_unit_index*interval ,(nrOfWorkers_client+work_unit_index*interval) )
          work_unit_index += nrOfWorkers_client

        case BeginMining_Server() =>
          println ("No. of worker actors on this server = " + nrOfWorkers_server)
          for (i <- 0 until nrOfWorkers_server) {
            val acref =  context.actorOf(Props(new Worker_Actor(nrOfZeroes, work_unit_index*interval ,(work_unit_index+1)*interval))) 
            server_actorsList += acref
            work_unit_index += 1
            }

          for (i <- 0 until nrOfWorkers_server) {
            println("Mining started on server actor: " + i)
            server_actorsList(i) ! Start_Mining()
          }

        case Bitcoin_Found(str) =>
          nrOfBitcoins += 1
          val hash_value = MessageDigest.getInstance("SHA-256")
          def hex_digest(s: String): String = {
            hash_value.digest(s.getBytes)
              .foldLeft("")((s: String, b: Byte) => s +
                Character.forDigit((b & 0xf0) >> 4, 16) +
                Character.forDigit(b & 0x0f, 16))
          }
          println("%s\t%s".format(str, hex_digest(str)))

        case WorkFinished_WorkerStopped() =>
          server_activeWorkers -=1
          println("No. of workers active: " + server_activeWorkers )
          val temp1: Boolean = ( server_activeWorkers== 0)
          if (temp1) {
            // Send the result to the listener
            listener ! Result(nrOfBitcoins, duration = (System.currentTimeMillis - start_time).millis)
                    
            context.stop(self)
          }


        case Client_shutdown() =>
          nrOf_activeClients -=1
          val temp2: Boolean = ( nrOf_activeClients== 0)
          if (temp1) {
            // Send the result to the listener
            listener ! Result(nrOfBitcoins, duration = (System.currentTimeMillis - start_time).millis)

            context.stop(self)
          }
      }

    }








    class Client_Master(acsys: ActorSystem, serverip: String) extends Actor {
      val nrOfWorkers_client:Int = Runtime.getRuntime().availableProcessors()

      val start_time: Long = System.currentTimeMillis
      val remoteServerMasterActor = context.actorFor("akka.tcp://ServerActorSystem@" + serverip + ":2700/user/server_master_actor")
      
      
      val interval = 10000000
      val client_actorsList: ArrayBuffer[ActorRef] = new ArrayBuffer[ActorRef]
      var client_activeWorkers: Int = nrOfWorkers_client



      def receive = {
        case ConnectToServer() =>
          remoteServerMasterActor ! GiveClientWork(nrOfWorkers_client)

        case BeginMining_Client(nrOfZeroes, startRange, endRange) =>
          println ("No. of worker actors on this client = " + nrOfWorkers_client)
          for (i <- 0 until nrOfWorkers_client)
          { println("Client worker_actor "+ i)
           val acref =  context.actorOf(Props(new Worker_Actor(nrOfZeroes, i*startRange, (i+1)*startRange))) 
           client_actorsList += acref
           }


          for (i <- 0 until nrOfWorkers_client){
            client_actorsList(i) ! Start_Mining()
          }


        case Bitcoin_Found(str) =>
          
          println("Bitcoin found by client: " )
          remoteServerMasterActor ! Bitcoin_Found(str)

        case WorkFinished_WorkerStopped =>
          client_activeWorkers-=1
          println("no. of clients :" + client_activeWorkers )
          if (client_activeWorkers==0) {
            println("Shutting down...")
            
            remoteServerMasterActor ! Client_shutdown()
            context.stop(self)
          }

        
      }
    }






    class Worker_Actor(nrOfZeroes: Int, startRange: Int, endRange: Int) extends Actor {
      val hash_value = MessageDigest.getInstance("SHA-256")
      def hex_digest(s: String): String = {
        hash_value.digest(s.getBytes)
          .foldLeft("")((s: String, b: Byte) => s +
            Character.forDigit((b & 0xf0) >> 4, 16) +
            Character.forDigit(b & 0x0f, 16))
        }

      def receive = {
        case Start_Mining() =>
          var continue = true
          var trailnum = startRange
          while (continue) {
            var strcmp = getNewString(trailnum)
            var hashval = hex_digest(strcmp.toString)
            if (isValidBitcoin(hashval, nrOfZeroes)) {
              sender ! Bitcoin_Found(strcmp)
              
            }
            trailnum += 1
            if (trailnum == (endRange) )  {
                continue = false
                context.stop(self)
              sender ! WorkFinished_WorkerStopped()
            }
          }
       }
     }










    

    
    class Listener extends Actor {
      def receive = {
        case Result(nrOfBitcoins, duration) =>
          println("\n\tTotal no. of bitcoins found: \t%s\n\tTime Taken: \t%s".format(nrOfBitcoins, duration))
          context.system.shutdown()
      }
    }

    def getNewString(trailnum: Int): String = {
      val gatorID = "punam.mahato"
      var trailnumB: BigInt = trailnum
      var trailnumstr = trailnumB.toString(36)
      var newString = gatorID.concat(trailnumstr)
      newString
    }

    def isValidBitcoin(str: String, nrOfZeroes: Int): Boolean = {
      def hashlimit(n: Int): String = {
        var A = ArrayBuffer[String]()
        var count: Int = 0
        while (count <= 63) {

          if (n == 0) A += "f"
          else A += "0"
          count = count + 1

        }
        if (n > 0) {
          A(n - 1) = "1"

        }
        A.mkString("")
      }

      val endval = hashlimit(nrOfZeroes)
      var result = (str < endval)
      return result
    }

    





    

    if (!args(0).isEmpty()) {
      if (args(0).contains('.')) {
        val clientsystem = ActorSystem("ClientActorSystem", ConfigFactory.load(clientConfig))

        
        //val listener = clientsystem.actorOf(Props[Listener], name = "listener")

        val client_master_actor = clientsystem.actorOf(Props(new Client_Master(clientsystem, args(0))), name = "clientMaster") 
        client_master_actor ! ConnectToServer() 
      } 
      else {
        val serversystem = ActorSystem("ServerActorSystem", ConfigFactory.load(serverConfig))

       
        val listener = serversystem.actorOf(Props[Listener], name = "listener")

       
        val server_master_actor = serversystem.actorOf(Props(new Server_Master(serversystem, args(0).toInt, listener)), name = "server_master_actor")

        server_master_actor ! BeginMining_Server()
      }
    }

  }
}
