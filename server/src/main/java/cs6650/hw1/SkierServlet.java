package cs6650.hw1;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@WebServlet(name = "SkierServlet", urlPatterns = {"/skiers/*"})
public class SkierServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    resp.setContentType("text/plain");
    resp.setStatus(HttpServletResponse.SC_OK);

    PrintWriter out = resp.getWriter();
    out.print("It works!");
    out.flush();
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    resp.setContentType("application/json");

    try {
      BufferedReader reader = req.getReader();
      JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

      if (!json.has("skierID")) {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        PrintWriter out = resp.getWriter();
        out.print("{\"error\": \"Missing skierID in request\"}");
        out.flush();
        return;
      }

      int skierID = json.get("skierID").getAsInt();
      resp.setStatus(HttpServletResponse.SC_CREATED);
      
      PrintWriter out = resp.getWriter();
      out.print("{\"message\": \"Lift ride recorded successfully\", \"skierID\": " + skierID + "}");
      out.flush();

    } catch (Exception e) {
      resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      PrintWriter out = resp.getWriter();
      out.print("{\"error\": \"Internal Server Error: " + e.getMessage() + "\"}");
      out.flush();
    }
  }
}
