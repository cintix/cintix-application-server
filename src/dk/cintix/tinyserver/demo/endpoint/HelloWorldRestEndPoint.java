/*
 */
package dk.cintix.tinyserver.demo.endpoint;

import dk.cintix.tinyserver.demo.model.Person;
import dk.cintix.tinyserver.demo.model.ResponseModel;
import dk.cintix.tinyserver.rest.annotations.Action;
import dk.cintix.tinyserver.rest.annotations.Cache;
import dk.cintix.tinyserver.rest.annotations.Inject;
import dk.cintix.tinyserver.rest.annotations.POST;
import dk.cintix.tinyserver.rest.annotations.Static;
import dk.cintix.tinyserver.rest.http.Status;
import dk.cintix.tinyserver.rest.http.request.RestHttpRequest;
import dk.cintix.tinyserver.rest.response.Response;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 *
 * @author cix
 */
public class HelloWorldRestEndPoint {

    @Inject
    RestHttpRequest request;

    @Cache(timeToLive = 2500) 
    @Action(path = "{name}")
    public Response sayHelloBack(String name) {
        return new Response().OK().model(new ResponseModel("Hello " + name));
    }

    @Cache(timeToLive = 2500, status = {Status.OK, Status.NotFound})
    @Cache(timeToLive = 1000, status = {Status.InternalServerError})
    @Action(path = "{name}/age/{age}")
    public Response sayHelloBackWithAge(String name, int age) {
        Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.YEAR, -age);
        return new Response().OK().model(new ResponseModel("Hello " + name + " your " + age + " old and born in " + calendar.get(Calendar.YEAR)));
    }

    @Static
    @Action(path = "world")
    public Response sayHelloToTheWorld() {
        System.out.println("request: " + request);
        return new Response().OK().model(new ResponseModel("Hello everyone!"));
    }

    @POST
    @Action(consume = "application/json")
    public Response register(Person person) {
        System.out.println("person " + person);
        return new Response().OK();
    }

}
