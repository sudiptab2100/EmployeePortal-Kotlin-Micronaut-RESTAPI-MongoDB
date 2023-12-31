package example.micronaut

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpStatus.CONFLICT
import io.micronaut.http.HttpStatus.CREATED
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/api/emp")
open class EmployeeController(private val employeeService: EmployeeRepository, private val employeeProducer: EmployeeProducer) {

    private val logger: Logger = LoggerFactory.getLogger(EmployeeController::class.java);
    companion object{
        val statusOf: MutableMap<String, Int> = mutableMapOf()
    }

    @Get("/all")
    fun list(): Publisher<Employee> = employeeService.list()

    @Get("/filter/{eid}")
    fun getById(eid: String) = employeeService.getByEID(eid)

    @Post("/update/{eid}/{field}/{value}")
    fun updateAField(eid: String, field: String, value: String): HttpResponse<Any> {
        val ss = listOf("eid", "name", "salary", "dept", "location")
        if (field !in ss) return HttpResponse.badRequest<CustomError>()
            .body(CustomError(
                HttpStatus.BAD_REQUEST.code,
                "Invalid Field",
                "Only Supported Fields: $ss"
            ))
        val res = employeeService.updateAField(eid, field, value).block()
        return HttpResponse.accepted<CustomOK>().body(CustomOK(
            HttpStatus.ACCEPTED.code,
            "Update Successful"
        ))
    }

//    @Post("/update/{eid}/{field}/{value}")
//    fun updateAField(eid: String, field: String, value: String) = employeeService.updateAField(eid, field, value)

    @Post("/drop/{eid}")
    fun dropAnEmployee(eid: String) = employeeService.dropAnEmployee(eid)

    @Post("/add") // <4>
    open fun save(@Valid employee: Employee): Mono<HttpStatus> { // <5>
        return employeeService.save(employee) // <6>
                .map { added: Boolean -> if (added) CREATED else CONFLICT }
    }

    @Post("/add-async")
    open fun saveAsync(@Valid employee: Employee): MutableHttpResponse<Any>? {
        logger.debug("\n\tReceived New Employee: {}", employee)
        statusOf[employee.eid] = 0
        employeeProducer.send(employee.eid, employee)
        var i = 0
        while(statusOf[employee.eid] == 0) {
            Thread.sleep(1000)
            logger.debug("Loop: {}", i)
            i++
        }
        return if(statusOf[employee.eid] == 1) {
            HttpResponse.accepted<CustomOK>().body(CustomOK(
                HttpStatus.ACCEPTED.code,
                "Employee Creation Successful"
            ))
        } else {
            HttpResponse.badRequest<CustomError>()
                .body(CustomError(
                    HttpStatus.BAD_REQUEST.code,
                    "Employee Creation Error",
                    "Employee ${employee.eid} couldn't be created"
                ))
        }
    }
}
