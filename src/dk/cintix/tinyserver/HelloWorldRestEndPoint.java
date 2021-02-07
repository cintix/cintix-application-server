/*
 */
package dk.cintix.tinyserver;

import dk.cintix.tinyserver.model.ResponseModel;
import dk.cintix.tinyserver.rest.annotations.Action;
import dk.cintix.tinyserver.rest.annotations.Inject;
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

    @Action(path = "/{name}")
    public Response sayHelloBack(String name) {
        return new Response().OK().model(new ResponseModel("Hello " + name));
    }

    @Action(path = "/{name}/age/{age}")
    public Response sayHelloBackWithAge(String name, int age) {
        Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.YEAR, -age);
        return new Response().OK().model(new ResponseModel("Hello " + name + " your " + age + " old and born in " + calendar.get(Calendar.YEAR)));
    }

    @Action(path = "/world")
    public Response sayHelloToTheWorld() {
        return new Response().OK().model(new ResponseModel("Hello everyone!"));
    }

}
