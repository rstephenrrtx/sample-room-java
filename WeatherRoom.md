This walk-through will demonstrate how you can use your Game On! room to access REST API data services.  We will be starting with the sample java file and adding code.  It is recommended for you to follow and setup your own sample java room first following the steps outlined here:  https://github.com/gameontext/sample-room-java
  
 For our example, we will be retrieving current weather conditions using the IBM Weather Company data service on IBM Bluemix.  
    Note: Other data sources could be used where data formats may vary.

#### Data service setup

In order to access the data, you will need to create a trial service on IBM Bluemix.  Login using your Bluemix account and look under **Catalog** then **Services**->**Data & Analytics**.  Scroll until you see **Weather Company Data** and click.  Review the terms and limits of the service and create a free account.
 
After creation, click on your service from your dashboard.  There are three tabs shown.  Under **Service Credentials** you will find the userid and password needed to access the REST APIs.  You can see these under the **View Credentials** action.  

Note:  The credentials are different from your Bluemix ID.  

Under **Manage** you can review the details of the service offerings.  Under **Get Started** we want to choose **APIs** to view this link:  https://twcservice.mybluemix.net/rest-api/

This page will show the REST APIs available.  Feel free to peruse the APIs and even try them by supplying your credentials.  We will use the **Current Conditions : Weather Observations**-> **Site-Based Current Conditions by Postal Code**.  Our example will focus on US based postal codes.

Now that your service has been created and you have explored the offerings, let's write some code!

#### Programatically accessing the API

You should have a cloned copy of the sample Java room.  We want to customize our room with a new command to find out "What's the weather like?" in a supplied zip code.  We will start by adding a custom command to the room.

##### Add command 

In the /src/main/java/org/gameontext/sample/RoomImplementation.java file, we need to make two additions.  We'll start by defining the command and the processing that occurs.  We'll add the command name to the `/help` command next.

We add a new command here:

```java
private void processCommand(String userId, String username, String content, RoomEndpoint endpoint, Session session)
```

The method uses a `switch` to process the commands.  Let's add `/weatherlike` as a case.  

Note:  All lowercase to process, mixed case for readability.  

While we process the `/weatherlike` command, let's parse the `remainder` variable to determine if we have five (5) numbers.  No need in making a call to the REST API if we don't have numbers (due to the limits of the free service).  We'll also add some feedback to the room visitor so they know something is going on.

Add `String zipCode;` to the beginning of the method.  

Just before the `default` of the `switch` statement, insert this code:

```java
               case "/weatherlike":
                // Custom command! /weatherlike is added to the room description in the @PostConstruct method
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
```

A few things to note:

1. Overly simplified `if...then...else` statements are used
2. Markdown notation is used for formatting.
3. If the `remainder` variable is not null, > 5 characters, and an integer - we'll process the number.
  * This does not indicate a valid US zip code is used
  * Extra credit if you want to explore another REST API to validate the number given is a valid zip code
  * Pre-validating the input provides faster response to the room visitor and saves a call to the REST API given the data limits presented.
4. We'll make the REST API call in the method here `weatherGet(zipCode, endpoint, session, userId, username);`

##### Add the command to the menu

In the /src/main/java/org/gameontext/sample/RoomImplementation.java file, we need to make two additions.  First, we want our command to display when we type in `/help`.  In the `postContruct()` method, paste in this line:

```java
// Customize the room
        roomDescription.addCommand("/weatherLike", "What's the weather like at <zipcode>");
```

This will add the command `/weatherLike` to the `/help` display letting visitors know what they can do.

#### Making the REST API call
In order to make the call, let's create a method

`public static void weatherGet(String zipC, RoomEndpoint endpoint, Session session, String userId, String username)`

Here we will 
  * Form our URL and connection
  * Retrieve the data from the service
  * Parse the data and format for display
  * Handle limited error conditions (for demo purposes)

#### Building the URL

If you refer back to the REST API page from the service documentation, we find the connection syntax we want to use is
`https://twcservice.mybluemix.net/api/weather/v1/location/78710%3A4%3AUS/observations.json?language=en-US&units=e` where *78710* is the zipcode for Austin, Tx.  We want to encode this and substitute our `zipC` variable so we create
```java
URL url = new URL("https://twcservice.mybluemix.net/api/weather/v1/location/"+zipC+"%3A4%3AUS/observations.json?language=en-US&units=e");
```
#### Making the connection

Let's also add additional connection information so we have

```java
                //uid/password will be unique to the Weather Company service you setup
                String uid="XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
                String password="YYYYYYYYYYY";
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty ("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString( (uid+":"+password).getBytes() ));
```

Note:  Use your userid and password from your service instance.

#### Verifying the connection

Now that we have the open connection, let's verify we have a good return code.  If not, display a message:

```java
       if (conn.getResponseCode() != 200) {
                   //No code here to handle every error condition.  Just display the error message.
                   String rc =  Integer.toString(conn.getResponseCode());
                   endpoint.sendMessage(session, Message.createBroadcastEvent("What's the weatherLike? " + username + ": " + zipC, userId, "Suddenly you hear a loud KLAXON HORN followed by a familar 'Danger, Will Robinson! Danger!'.  You look at the instrument panel and read: \n\nAttempted to find the Current Weather conditions for " + zipC + " but instead received this HTTP response code: \n\n " + rc + " " + conn.getResponseMessage()));
                }
```

#### Parse the returned data

The HTTPS connection returns a string formatted as JSON.  But there are several ways that JSON information could be categorized - which is correct for us?  To figure that out, we need to go back to the service REST API page.

We can start by looking at the details for the REST API.  We find both a *Model* and *Example Value* entry.  With *Model*, the data is described with field definitions.  *Example Value* shows us what the data would really look like.

In our example, the returned data contains two JSON objects.  The first is named *metadata* while the second is *observation*.  If you look at the data for the "3-Day Intraday Forecast by Postal Code" API you will see two objects are returned but the second is a JSONArray.  Since we want to see the values in *observation*, we access that object for our *result* variable.

Now that we have a good return, let's get the data, format it, and display the current weather conditions.
```java
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
```


Of course, there may be times when the connection is not made so we place all of the code in a `try/catch` statement and display appropriate messages if desired.  When we are through the full code looks like this:

```java
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

              endpoint.sendMessage(session, Message.createBroadcastEvent("What's the weatherLike? " + username + ": " + zipC, userId, "MalformedURLException of some type. "));

          } catch (IOException e) {
              endpoint.sendMessage(session, Message.createBroadcastEvent("What's the weatherLike? " + username + ": " + zipC, userId,"IOException of some type."));

          }

        }
}
```
Note:  More advanced error handling and parsing is left as an exercise for the reader.

#### Viewing the room

With the code in place, you should be able to build and update your changes.  

As you `/teleport` to your room, you can try out the `/help` command to view your new command.  Give the new `/weatherLike` command a try with 
  * No parameters
  * Too short of a parameter
  * Non-numeric values

When a valid zip code is provided, you will see success such as this:
```
/weatherLike 78742

The instruments hum and the lights fade in and out.

Suddenly you hear a loud WHOOSH followed by a familar TADA! You look at the instrument panel and read:

The weather condition in 78742 is:

Austin/Bergstrom reports the weather is Fair and 82°F. Wind is S at 16 Mph.

```

As you enter zip codes, you may find a value that is invalid.  An example would be:

```
/weatherLike 54321

The instruments hum and the lights fade in and out.

Suddenly you hear a loud KLAXON HORN followed by a familar 'Danger, Will Robinson! Danger!'. You look at the instrument panel and read:

Attempted to find the Current Weather conditions for 54321 but instead received this HTTP response code:

400 Bad Request
```
We have a good idea that the 400 return code is a bad zip code (as that is the only part of the API we are dynamically supplying).  Other possible values are detailed on the REST API page.

#### Summary

while I am familiar with Java programming, I am by no means an expert.  This activity provided a few key lessons:
  * Learning the Game On! room implementation
  * An introduction to microservices
  * A practical use of Bluemix services
  * REST API interaction

I probably made it harder than it needed to be so I hope you learn from my experience.

