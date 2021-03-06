import javax.inject._
import com.digitaltangible.playguard.GuardFilter
import play.api._
import play.api.http.DefaultHttpFilters
import play.filters.cors.CORSFilter
import play.filters.headers.SecurityHeadersFilter

/**
  * This class configures filters that run on every request. This
  * class is queried by Play to get a list of filters.
  *
  * Play will automatically use filters from any class called
  * `Filters` that is placed the root package. You can load filters
  * from a different class by adding a `play.http.filters` setting to
  * the `application.conf` configuration file.
  *
  * @param env           Basic environment settings for the current application.
  * @param guardFilter   the guardFilter to test with this sample app
  */
@Singleton
class Filters @Inject()(env: Environment,
                        corsFilter: CORSFilter,
                        securityHeadersFilter: SecurityHeadersFilter,
                        guardFilter: GuardFilter
                       ) extends DefaultHttpFilters(
  corsFilter,
  securityHeadersFilter,
  guardFilter
)