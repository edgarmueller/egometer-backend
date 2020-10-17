# egometer-backend

This is the backend part of egometer. See [egometer-frontend](https://github.com/edgarmueller/egometer-frontend) for the React-based frontend.

## Getting started

* Install MongoDB and start it, see [tutorial](https://docs.mongodb.com/v3.2/tutorial/install-mongodb-on-ubuntu/) for a how-to
* Import example data from the `data` via `mongoimport` directory:
```
mongoimport --db egometer --collection schemas --file schemas.json
```
* Create a `application.local.conf` configuration file to include the following configuration:

```
SWAGGER_API_URL = "http://localhost:9000"

API_URL = "http://localhost:9000"

FRONTEND_URL = "http://localhost:3000"

MAIL_USER = "<username>@gmail.com"

MAIL_PASSWORD = "<password>"

MONGODB_URI = "mongodb://localhost:27017/egometer"

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

include "application.conf"      
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

* Run SBT and type ```~run```  to launch the server in dev mode

Then open your favourite browser and go to

```localhost:9000/api-docs```

## How to run the tests
In the tests directory there are tests written with the [ScalaTest](http://www.scalatest.org/) library.  
To launch them just type ```test``` in a running SBT session or simply type ```sbt test```

## API
This project comes with Swagger Docs, to check them navigate to `http://localhost:9000/api/v1/docs.

### Sign-in
```bash
curl --location --request POST 'localhost:9000/api/v1/sign-in' \
--header 'Content-Type: application/json' \
--data-raw '{
	"email": "foo@example.com",
	"password": "test1234",
	"rememberMe": false
}'
```

### Get meters 
```bash
curl --location --request GET 'localhost:9000/api/v1/meters' \
--header 'X-Auth-Token: $TOKEN' \
--header 'Content-Type: application/json' \
--data-raw '{
	"email": "foo@example.com",
	"password": "secret",
	"rememberMe": false
}'
```
