/*******************************************************************************
 * Copyright (c) 2016 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.gameontext.sample;

import java.util.Locale;
import java.util.logging.Level;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonArray;
//import java.lang.Object;
//import org.json.JSONObject;
import javax.websocket.Session;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import javax.net.ssl.HttpsURLConnection;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.gameontext.sample.map.client.MapClient;
import org.gameontext.sample.protocol.Message;
import org.gameontext.sample.protocol.RoomEndpoint;

/**
 * Here is where your room implementation lives. The WebSocket endpoint
 * is defined in {@link RoomEndpoint}, with {@link Message} as the text-based
 * payload being sent on the wire.
 * <p>
 * This is an ApplicationScoped CDI bean, which means it will be started
 * when the server/application starts, and stopped when it stops.
 *
 */
@ApplicationScoped
public class RoomImplementation {

    public static final String LOOK_UNKNOWN = "It doesn't look interesting";
    public static final String UNKNOWN_COMMAND = "This room is run by highly trained hamsters. Sadly, their training is limited and they don't understand `%s`";
    public static final String UNSPECIFIED_DIRECTION = "You didn't say which way you wanted to go so you just stand there...hoping the wind will move you.  It doesn't.";
    public static final String UNKNOWN_DIRECTION = "There isn't a door in that direction (%s)";
    public static final String GO_FORTH = "You head %s";
    public static final String HELLO_ALL = "%s is here";
    public static final String HELLO_USER = "Welcome!";
    public static final String GOODBYE_ALL = "%s has gone";
    public static final String GOODBYE_USER = "Bye!";

    /**
     * The room id: this is translated from the ROOM_ID environment variable into
     * a JNDI value by server.xml (Liberty)
     */
    @Resource(lookup = "roomId")
    protected String roomId;

    @Inject
    protected MapClient mapClient;

    protected RoomDescription roomDescription = new RoomDescription();

    @PostConstruct
    protected void postConstruct() {

        if ( roomId == null || roomId.contains("ROOM_ID") ) {
            // The room id was not set by the environment; make one up.
            roomId = "TheGeneratedIdForThisRoom";
        } else {
            // we have a custom room id! let's see what the map thinks.
            mapClient.updateRoom(roomId, roomDescription);
        }

        // Customize the room
        roomDescription.addCommand("/weatherLike", "What's the weather like at <zipcode>");

        Log.log(Level.INFO, this, "Room initialized: {0}", roomDescription);
    }

    @PreDestroy
    protected void preDestroy() {
        Log.log(Level.FINE, this, "Room to be destroyed");
    }

    public void handleMessage(Session session, Message message, RoomEndpoint endpoint) {

        // If this message isn't for this room, TOSS IT!
//        if ( !roomId.equals(message.getTargetId()) ) {
//            Log.log(Level.FINEST, this, "Received message for the wrong room ({0}): {1}", message.getTargetId(), message);
//            return;
//        }

        // Fetch the userId and the username of the sender.
        // The username can change overtime, so always use the sent username when
        // constructing messages
        JsonObject messageBody = message.getParsedBody();
        String userId = messageBody.getString(Message.USER_ID);
        String username = messageBody.getString(Message.USERNAME);

        Log.log(Level.FINEST, this, "Received message from {0}({1}): {2}", username, userId, messageBody);

        // Who doesn't love switch on strings in Java 8?
        switch(message.getTarget()) {

        case roomHello:
            //		roomHello,<roomId>,{
            //		    "username": "username",
            //		    "userId": "<userId>",
            //		    "version": 1|2
            //		}
            // See RoomImplementationTest#testRoomHello*

            // Send location message
            endpoint.sendMessage(session, Message.createLocationMessage(userId, roomDescription));

            // Say hello to a new person in the room
            endpoint.sendMessage(session,
                    Message.createBroadcastEvent(
                            String.format(HELLO_ALL, username),
                            userId, HELLO_USER));
            break;

        case roomJoin:
            //		roomJoin,<roomId>,{
            //		    "username": "username",
            //		    "userId": "<userId>",
            //		    "version": 2
            //		}
            // See RoomImplementationTest#testRoomJoin

            // Send location message
            endpoint.sendMessage(session, Message.createLocationMessage(userId, roomDescription));

            break;

        case roomGoodbye:
            //		roomGoodbye,<roomId>,{
            //		    "username": "username",
            //		    "userId": "<userId>"
            //		}
            // See RoomImplementationTest#testRoomGoodbye
            // Remove the 'weatherLike' command from the list of commands
            //roomDescription.removeCommand("/weatherLike");

            // Say goodbye to person leaving the room
            endpoint.sendMessage(session,
                    Message.createBroadcastEvent(
                            String.format(GOODBYE_ALL, username),
                            userId, GOODBYE_USER));
            break;

        case roomPart:
            //		room,<roomId>,{
            //		    "username": "username",
            //		    "userId": "<userId>"
            //		}
            // See RoomImplementationTest#testRoomPart

            break;

        case room:
            //		room,<roomId>,{
            //		    "username": "username",
            //		    "userId": "<userId>"
            //		    "content": "<message>"
            //		}
            String content = messageBody.getString(Message.CONTENT);

            if ( content.charAt(0) == '/' ) {
                // command
                processCommand(userId, username, content, endpoint, session);
            } else {
                // See RoomImplementationTest#testHandleChatMessage

                // echo back the chat message
                endpoint.sendMessage(session,
                        Message.createChatMessage(username, content));
            }
            break;

        default:
            // unknown message type. discard, don't break.
            break;
        }
    }

    private void processCommand(String userId, String username, String content, RoomEndpoint endpoint, Session session) {
        // Work mostly off of lower case.
        String contentToLower = content.toLowerCase(Locale.ENGLISH).trim();

        String firstWord;
        String remainder;
        String zipCode;

        int firstSpace = contentToLower.indexOf(' '); // find the first space
        if ( firstSpace < 0 || contentToLower.length() <= firstSpace ) {
            firstWord = contentToLower;
            remainder = null;
        } else {
            firstWord = contentToLower.substring(0, firstSpace);
            remainder = contentToLower.substring(firstSpace+1);
        }

        switch(firstWord) {
            case "/go":
                // See RoomCommandsTest#testHandle*Go*
                // Always process the /go command.
                String exitId = getExitId(remainder);

                if ( exitId == null ) {
                    // Send error only to source session
                    if ( remainder == null ) {
                        endpoint.sendMessage(session,
                                Message.createSpecificEvent(userId, UNSPECIFIED_DIRECTION));
                    } else {
                        endpoint.sendMessage(session,
                                Message.createSpecificEvent(userId, String.format(UNKNOWN_DIRECTION, remainder)));
                    }
                } else {
                    // Allow the exit
                    endpoint.sendMessage(session,
                            Message.createExitMessage(userId, exitId, String.format(GO_FORTH, prettyDirection(exitId))));
                }
                break;

            case "/look":
            case "/examine":
                // See RoomCommandsTest#testHandle*Look*

                // Treat look and examine the same (though you could make them do different things)
                if ( remainder == null || remainder.contains("room") ) {
                    // This is looking at or examining the entire room. Send the player location message,
                    // which includes the room description and inventory
                    endpoint.sendMessage(session, Message.createLocationMessage(userId, roomDescription));
                } else {
                    endpoint.sendMessage(session,
                            Message.createSpecificEvent(userId, LOOK_UNKNOWN));
                }
                break;

            case "/weatherlike":
                // Custom command! /ping is added to the room description in the @PostConstruct method
                // See RoomCommandsTest#testHandlePing*
                endpoint.sendMessage(session, Message.createBroadcastEvent("What's the weatherLike? " + username, userId, "The instruments hum and the lights fade in and out.  \n\n"));

                if ( remainder == null ) {
                    endpoint.sendMessage(session, Message.createBroadcastEvent("What's the weatherLike? " + username, userId, "You concentrate really, really hard.\n\nYou quietly look around and glance at the instrument panel and read:\n\n 'It's room temperature.  Try typing a zip code with the command.'"));

                } else {
                    //Need to pre-process the remainder to ensure
                    //   a) there are 5 characters
                    //   b) the characters are numbers.  Won't validate the numbers are a valid zipcode.  We're not gurus.
                    if (remainder.length() < 5) {
                       //message that we need 5 characters for a valid zip
                       endpoint.sendMessage(session, Message.createBroadcastEvent("What's the weatherLike? " + username + ": " + remainder, userId, "Suddenly you hear a loud CLANK!  You look at the instrument panel and read:\n\n 'Whoopsie!  You need at least 5 digits for a valid zip code.  Try again.'  "));
                    }
                    else {
                       // There are 5 characters, are they numbers?i
                       try
                       {
                        // the String to int conversion happens here
                        int i = Integer.parseInt(remainder.trim());
                        // Conversion worked, let's get the zipCode as string
                        zipCode = remainder.substring(0,5);
                        weatherGet(zipCode, endpoint, session, userId, username);
                       }
                       catch (NumberFormatException nfe)
                       {
                        // If we get here, the conversion failed!  It wasn't a numeric value so print a message
                        // This doesn't mean it is a valid zip code, just means there were non-numeric characters entered.
                        endpoint.sendMessage(session, Message.createBroadcastEvent("What's the weatherLike? " + username + ": " + remainder, userId, "Suddenly you hear a loud KER-THUNK!  You look at the instrument panel and read:\n\n Are you trying to choke me?  You need 5 NUMBERS for a valid zip code.  I'm not that smart.  Try again.  "));

                       }
                    }
                }
                break;

            default:
                endpoint.sendMessage(session,
                        Message.createSpecificEvent(userId, String.format(UNKNOWN_COMMAND, content)));
                break;
        }
    }


    /**
     * Given a lower case string describing the direction someone wants
     * to go (/go N, or /go North), filter or transform that into a recognizable
     * id that can be used as an index into a known list of exits. Always valid
     * are n, s, e, w. If the string doesn't match a known exit direction,
     * return null.
     *
     * @param lowerDirection String read from the provided message
     * @return exit id or null
     */
    protected String getExitId(String lowerDirection) {
        if (lowerDirection == null) {
            return null;
        }

        switch(lowerDirection) {
            case "north" :
            case "south" :
            case "east" :
            case "west" :
                return lowerDirection.substring(0,1);

            case "n" :
            case "s" :
            case "e" :
            case "w" :
                // Assume N/S/E/W are managed by the map service.
                return lowerDirection;

            default  :
                // Otherwise unknown direction
                return null;
        }
    }

    /**
     * From the direction we used as a key
     * @param exitId The exitId in lower case
     * @return A pretty version of the direction for use in the exit message.
     */
    protected String prettyDirection(String exitId) {
        switch(exitId) {
            case "n" : return "North";
            case "s" : return "South";
            case "e" : return "East";
            case "w" : return "West";

            default  : return exitId;
        }
    }

    public boolean ok() {
        return mapClient.ok();
    }

    public static void weatherGet(String zipC, RoomEndpoint endpoint, Session session, String userId, String username){
    try {

                String output = "";
                //Build our URL with the zipCode
		URL url = new URL("https://twcservice.mybluemix.net/api/weather/v1/location/"+zipC+"%3A4%3AUS/observations.json?language=en-US&units=e");
                //uid/password will be unique to the Weather Company service you setup
                String uid="8b1ee30c-411a-41d3-bf9d-76f30216ba5a";
                String password="UBvuhrHnTU";
		HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty ("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString( (uid+":"+password).getBytes() ));
		if (conn.getResponseCode() != 200) {
                   //No code here to handle every error condition.  Just display the error message.
                   String rc =  Integer.toString(conn.getResponseCode());
                   endpoint.sendMessage(session, Message.createBroadcastEvent("What's the weatherLike? " + username + ": " + zipC, userId, "Suddenly you hear a loud KLAXON HORN followed by a familar 'Danger, Will Robinson! Danger!'.  You look at the instrument panel and read: \n\nAttempted to find the Current Weather conditions for " + zipC + " but instead received this HTTP response code: \n\n " + rc + " " + conn.getResponseMessage()));
		}
                //We have the connection conn, get the data stream using createReader
                JsonReader rdr = Json.createReader(conn.getInputStream());
                //Read the JsonReader into a JsonObject
                JsonObject obj = rdr.readObject();
                //Since the data returns 2 JsonObjects named "metadata" and "observation", let's get the data for the observation as our result
                JsonObject result = obj.getJsonObject("observation");
                // When we review the actual datastream, we find each field and assign to a variable.
                // As we want to display the values as text, we cast the numeric values to String.
                // We retrieve the value by stating the key name
                String wName=result.getString("obs_name");
                String wPhrase=result.getString("wx_phrase");
                String wTemp=Integer.toString(result.getInt("temp"));
                String wWdir=result.getString("wdir_cardinal");
                String wWsp=Integer.toString(result.getInt("wspd"));
                // Here we build the weather report phrase by combining the above variables with some formatting.
                String wReport = wName+" reports the weather is "+wPhrase+ " and " +wTemp+"°F.  Wind is "+wWdir+" at "+wWsp+" Mph.";
                endpoint.sendMessage(session, Message.createBroadcastEvent("What's the weatherLike? " + username + ": " + zipC, userId, "Suddenly you hear a loud WHOOSH followed by a familar TADA!  You look at the instrument panel and read: \n\nThe weather condition in " + zipC + " is:\n\n"+wReport));
		conn.disconnect();

	  } catch (MalformedURLException e) {

//        	endpoint.sendMessage(session, Message.createBroadcastEvent("What's the weatherLike? " + username + ": " + zipC, userId,	"MalformedURLException of some type. "));

	  } catch (IOException e) {

//	        endpoint.sendMessage(session, Message.createBroadcastEvent("What's the weatherLike? " + username + ": " + zipC, userId,"Error message:"));

	  }

	}
}
