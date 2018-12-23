# egometer-backend

Backend for egometer

## Getting started

* Install MongoDB and start it, see [tutorial](https://docs.mongodb.com/v3.2/tutorial/install-mongodb-on-ubuntu/) for a how-to
* Create a `application.local.conf` configuration file to include the following configuration:

```
      swagger.api.uri = "http://localhost:9000"

      mongodb.uri = "mongodb://localhost:27017:/egometer"

      ui.url = "http://localhost:3000"

      play.filters {
        cors {
          allowedOrigins = null
        }
        hosts {
          allowed = ["."]
        }
      }

      play.mailer {
        host = "smtp.gmail.com"
        port = 587
        ssl  = false
        tls  = true
        user = "<username>@gmail.com"
        password = "<password>"
      }
```

* Create a `silhouette.local.conf` file to configure google client settings of the app

```
      silhouette {
        google.clientID="<id>.apps.googleusercontent.com"
        google.clientSecret="<secret>"
        authenticator.sharedSecret="<changeme>"
        authenticator.crypter.key="<changeme>"
      }
```

* Import example data from the `data` via `mongoimport` directory:

```
      mongoimport --db egometer --collection schemas --file schemas.json
```

* Run SBT and type ```~run```  to launch the server in dev mode

Then open your favourite browser and go to

```localhost:9000/api-docs```

## How to run the tests
In the tests directory there are tests written with the [ScalaTest](http://www.scalatest.org/) library.  
To launch them just type ```test``` in a running SBT session or simply type ```sbt test```



