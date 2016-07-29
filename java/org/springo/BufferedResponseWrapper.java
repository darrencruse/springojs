package org.springo;

/*
 * BufferedResponseWrapper.java
 *
 * This class wraps a servlet response and buffers it's output so that the
 * javascript interceptor has the option of modifying it before it's sent
 * back to the client.
 *
 * This code was derived from an example in "Professional XML Development
 * with Apache Tools".  The debug wrapper methods are from the SiteMesh
 * "DebugResponseWrapper" class (these may be helpful to see exactly what
 * methods are getting called, e.g. during request processing in Spring).
 */

import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.log4j.Logger;

public class BufferedResponseWrapper extends HttpServletResponseWrapper {

 private static Logger log = Logger.getLogger(BufferedResponseWrapper.class);

 // Are we buffering the response?
 // (otherwise let things through to the wrapped ServletResponse)
 private boolean buffering = true;

 private int overriddenHttpStatusCode = 200;
 private String overriddenHttpStatusMsg = "";

 // When rendering JSPs, etc. Spring makes use of:
 ByteArrayOutputStream bufferedStream = new ByteArrayOutputStream();

 // But Velocity does character writes:
 CharArrayWriter bufferedChars = null;

 private static int responseWrapperNumber = 0;

 /**
  * The constructor receives the servlet response to be wrapped.
  * @param res
  */
 public BufferedResponseWrapper(HttpServletResponse res) {
  super(res);
  responseWrapperNumber++;
  debug("<CONSTRUCT>", null, null);
 }

 /**
  * Buffer the response?
  *
  * Normally, the BufferedResponseWrapper starts out buffering
  * by default, and you simply call this method once when you
  * are ready to write the response back to the client.  At that
  * point calls through the wrapper are allowed through to the
  * wrapped ServletResponse, and to the client.
  * @param buffer
  */
 public void setBuffering(boolean buffer) {
  this.buffering = buffer;
 }

 /**
  * Get the output stream for the servlet response.
  *
  * When buffering, a stream is returned which captures all
  * the bytes written to it.
  *
  * When not buffering, the actual output stream is returned
  * which writes back to the client.
  */
 @Override
 public ServletOutputStream getOutputStream() throws IOException {
  debug("getOutputStream", null, null);
  if (buffering) {
   return new ServletByteArrayOutputStream(bufferedStream);
  } else {
   return super.getOutputStream();
  }
 }

 /**
  * Get the print writer for the servlet response.
  *
  * When buffering, a writer is returned which captures all
  * the bytes printed to it.
  *
  * When not buffering, the actual writer is returned
  * which prints back to the client.
  */
 @Override
 public PrintWriter getWriter() throws IOException {
  debug("getWriter", null, null);

  if (buffering) {
   //			bufferedChars = new CharArrayWriter();
   return new BufferedPrintWriter(bufferedStream);
  } else {
   return super.getWriter();
  }
 }

 /**
  * Get the buffered response body.
  *
  * This is the data captured during the time that "buffering" was true.
  *
  * @return
  */
 public String getBody() {
  String result = "";
  try {
   //			if(bufferedChars != null) {
   //				result = bufferedChars.toString();
   //			}

   if ((result == null || "".equals(result)) &&
    bufferedStream != null) {
    result = bufferedStream.toString("UTF-8");
   }

   if (result == null) {
    result = "";
   }
  } catch (UnsupportedEncodingException e) {}
  return result;
 }

 /**
  * Set the response body to be returned to the client.
  *
  * Note:  Spring generates it's desired response body by writing
  *        via the getWriter/getOutputStream interface like a
  *        normal servlet.  This method has been added for the
  *        convenience of javascript interception.
  * @param body
  * @throws IOException
  */
 public void setBody(String body) throws IOException {
  // Clear what used to be in the buffer.
  if (bufferedStream != null) {
   bufferedStream.reset();
  }
  if (bufferedChars != null) {
   bufferedChars.reset();
  }
  ServletOutputStream out = getOutputStream();
  out.print(body);
 }

 /**
  * Flush the buffer to the output stream.
  *
  * Note:  Normally, flushing the buffer causes the response
  *        to be "committed", after which the isCommitted()
  *        method returns true, and any subsequent changes to
  *        headers, response status code, etc. become disallowed.
  *        For this reason, we intentionally block flushes while
  *        "buffering" is true.
  */
 @Override
 public void flushBuffer() throws IOException {
  debug("flushBuffer", null, null);
  if (!buffering) {
   super.flushBuffer();
  }
 }

 /**
  * Set the response status code.
  *
  * While buffering is true we intercept the status
  * code and save it in our own override area.
  */
 @Override
 public void setStatus(int sc) {
  debug("setStatus", String.valueOf(sc), null);
  if (buffering) {
   overriddenHttpStatusCode = sc;
  } else {
   super.setStatus(sc);
  }
 }

 /**
  * Set the response status code and response status message.
  *
  * While buffering is true we intercept the status
  * code and message and save them in our own override area.
  */
 @Override
 public void setStatus(int sc, String msg) {
  debug("setStatus", String.valueOf(sc), msg);

  if (buffering) {
   overriddenHttpStatusCode = sc;
   overriddenHttpStatusMsg = msg;
  } else {
   super.setStatus(sc, msg);
  }
 }

 /**
  * Get the buffered/overridden status code.
  * @return
  */
 public int getStatus() {
  return overriddenHttpStatusCode;
 }

 /**
  * Get the buffered/overridden status message.
  * @return
  */
 public String getStatusMsg() {
  return overriddenHttpStatusMsg;
 }

 @Override
 public void sendError(int sc) throws IOException {
  debug("sendError", String.valueOf(sc), null);
  if (buffering) {
   overriddenHttpStatusCode = sc;
  }
  super.sendError(sc);
 }

 @Override
 public void sendError(int sc, String msg) throws IOException {
  debug("sendError", String.valueOf(sc), msg);
  if (buffering) {
   overriddenHttpStatusCode = sc;
  }
  super.sendError(sc, msg);
 }

 //
 // The following methods are simply debugging aids.
 // They can safely be removed from this class and
 // should not change the functioning of the wrapper,
 // but have been left because they may prove helpful
 // for debugging should problems arise in the future.
 //

 @Override
 public void setHeader(String name, String value) {
  debug("setHeader", name, value);
  super.setHeader(name, value);
 }

 @Override
 public void setDateHeader(String name, long date) {
  debug("setDateHeader", name, String.valueOf(date));
  super.setDateHeader(name, date);
 }

 @Override
 public void addCookie(Cookie cookie) {
  debug("addCookie", cookie.getName(), cookie.toString());
  super.addCookie(cookie);
 }

 @Override
 public void addDateHeader(String name, long date) {
  debug("addDateHeader", name, String.valueOf(date));
  super.addDateHeader(name, date);
 }

 @Override
 public void addHeader(String name, String value) {
  debug("addHeader", name, value);
  super.addHeader(name, value);
 }

 @Override
 public void addIntHeader(String name, int value) {
  debug("addIntHeader", name, String.valueOf(value));
  super.addIntHeader(name, value);
 }

 @Override
 public void sendRedirect(String location) throws IOException {
  debug("sendRedirect", location, null);
  super.sendRedirect(location);
 }

 @Override
 public void setIntHeader(String name, int value) {
  debug("setIntHeader", name, String.valueOf(value));
  super.setIntHeader(name, value);
 }

 @Override
 public void reset() {
  debug("reset", null, null);
  super.reset();
 }

 @Override
 public void setBufferSize(int size) {
  debug("setBufferSize", String.valueOf(size), null);
  super.setBufferSize(size);
 }

 @Override
 public void setContentLength(int len) {
  debug("setContentLength", String.valueOf(len), null);
  super.setContentLength(len);
 }

 @Override
 public void setContentType(String type) {
  debug("setContentType", type, null);
  super.setContentType(type);
 }

 @Override
 public void setLocale(Locale locale) {
  debug("setBufferSize", locale.getDisplayName(), null);
  super.setLocale(locale);
 }

 /**
  * Output detailed log messages?
  * (they will come out only if this is true *and* DEBUG level logs are enabled)
  * @return
  */
 private boolean logDetails() {
  return false; // this is very low level stuff switch to true for local debugging only
 }

 private void debug(String methodName, String arg1, String arg2) {

  if (logDetails() && log.isDebugEnabled()) {
   StringBuffer s = new StringBuffer();
   s.append("[debug ");
   s.append(responseWrapperNumber);
   s.append(", buffering=");
   s.append(buffering);
   s.append(", isCommitted=");
   s.append(this.isCommitted());
   s.append("] ");
   s.append(methodName);
   s.append("()");
   if (arg1 != null) {
    s.append(" : '");
    s.append(arg1);
    s.append("'");
   }
   if (arg2 != null) {
    s.append(" = '");
    s.append(arg2);
    s.append("'");
   }

   log.debug(s);
  }
 }

}
