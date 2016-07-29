/**
 * @fileOverview Middleware to catch (thrown) application errors and generate
 * a *standard* JSON error response to the RESTful client.
 *
 * The point of this middleware is that your RESTful service api
 * should *guarantee* a standard documented error format is being sent to
 * your client in *all* circumstances (even when unanticipated exceptions
 * are thrown!).
 *
 * Because only you know the exact format for *your* RESTful api error
 * responses, and only you know precisely what you put in the exceptions
 * that you throw, you configure this middleware with a couple functions
 * to customize the error handling to your needs.
 *
 * @example
 * app.configure("notfound", "error", "jsonerror", "modifyrequestbody");
 * app.jsonerrorisapperror(function(appError, request) {
 *   return (request.pathInfo.indexOf('.json') !== -1 &&
 *          appError.type && appError.message);
 * });
 * app.jsonerrorconverter = function(appError) {
 *   // if status is there in the error add the status to response.
 *   var status = (appError.status) ? appError.status : 422;
 *   delete appError.status;
 *
 *   // This error *should* be handled at the application level -
 *   // log it so we know it was not:
 *   log.error("An unexpected issue has occurred. Please visit the 'Help' link to contact support if you continue to see this message." +
 *       appError.type + " (" + appError.message +")");
 *
 *   // Return the error in our standard JSON format.
 *   return utils.buildErrorResponse([ appError ],status);
 * });
 *
 */

include('ringo/webapp/response');
var log = require("ringo/logging").getLogger("jsonerror");

/**
 * Stick middleware to format (thrown) errors as a standard JSON error response.
 * @param {Function} next the wrapped middleware chain
 * @param {Object} app the Stick Application object
 * @returns {Function} a JSGI middleware function
 */

exports.middleware = function jsonerror(next, app) {

  // you can check if a catch exception object is really one of your "app errors" with:
  app.jsonerrorisapperror = function(appError, request) { return true; };

  // convert a caught exception error object to an actual JSGI response:
  app.jsonerrorconverter = function(appError) {
    	return new Response(JSON.stringify(appError));
  };

	return function jsonerror(request) {
		try {
			return next(request);
		} catch (appError) {
			if(!app.jsonerrorisapperror(appError, request)) {
				throw appError;  // re-throw let others handle this.
			}
			else {
        // convert the exception to a JSGI Response in standard format:
        return app.jsonerrorconverter(appError);
			}
		}
	}

};
