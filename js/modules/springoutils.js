/**
 * @fileOverview Some helper/utility functions for Springo
 *
 * @author darrencruse (https://github.com/darrencruse)
 */

var log = require('ringo/logging').getLogger(module.id);
include('ringo/webapp/response');

importClass(org.springo.BufferedResponseWrapper);
importClass(org.springo.RingoJsgiFilter);

/**
 * Forward the request to the Spring Dispatcher.
 *
 * Note:  This will stream Spring's output straight to the client,
 *        preventing any further modification of the response.
 *
 * @param {Request} the request to be forwarded
 * @param {Regex} (optional) from the url pattern to forward, default is \/.*\/api\/
 * @param {String} (optional) to the String to replace for "from", default is \/_api\/
 * @returns {Response} the JSGI Response, or null if the request url did not match "from"
 */
exports.forwardtospringdispatcher = function(request, from, to) {

	if(!from) {
		from =  /\/.*\/api\//;
	}

	if(!to) {
		 to = '\/_api\/';
	}

	var originalRequestPath = request.env.servletRequest.requestURI;
	if(from.test(originalRequestPath)) {
		// Yup this is a portal service request - forward it on to the internal Spring url:
		var springRequestPath = originalRequestPath.replace(from, to);
		var requestDispatcher = request.env.servletRequest.getRequestDispatcher(springRequestPath);
		if(requestDispatcher) {
			requestDispatcher.forward(request.env.servletRequest,request.env.servletResponse);
		}
		else
		{
			return new Response("Failed to forward request to: " + springRequestPath);
		}
		return new Response("");
	}
	else {
		// This is not a matching request - let the caller know by simply returning null:
		return null;
	}
};

/**
 * Forward the request to the Spring Dispatcher.
 *
 * Note:  This will capture Spring's output and return it as a
 *        JSGI Response object.
 *
 * @param {Request} the request to be forwarded
 * @param {Regex} (optional) from the url pattern to forward, default is \/.*\/api\/
 * @param {String} (optional) to the String to replace for "from", default is \/_api\/
 * @returns {Response} the JSGI Response, or null if the request url did not match "from"
 */
exports.capturefromspringdispatcher = function(request, from, to) {

	// capturing of spring's output can be prevented if desired:

	// either through a url parameter
	var springcapture = request.params["springcapture"];
	if((typeof springcapture !== "undefined") &&
		(springcapture === "false" ||
		springcapture === "no" ||
		springcapture === "none")
	) {
		return exports.forwardtospringdispatcher(request, from, to);
	}

	// or through a request attribute:
	springcapture = request.springcapture;
	if((typeof springcapture !== "undefined") &&
		!springcapture) {
		return exports.forwardtospringdispatcher(request, from, to);
	}

	if(!from) {
		from =  /\/.*\/api\//;
	}

	if(!to) {
		 to = '\/_api\/';
	}

	var originalRequestPath = request.env.servletRequest.requestURI;
	if(from.test(originalRequestPath)) {
		// Yup this is a portal service request - forward it on to the internal Spring url:
		var springRequestPath = originalRequestPath.replace(from, to);
		var requestDispatcher = request.env.servletRequest.getRequestDispatcher(springRequestPath);
		if(requestDispatcher) {

			// Wrap the response object so we can capture it's output:
			var originalResponseObject = request.env.servletResponse;
			var responseWrapper = new BufferedResponseWrapper(originalResponseObject);
			request.env.servletResponse = responseWrapper;

			requestDispatcher.forward(request.env.servletRequest,request.env.servletResponse);

			var responseBody = responseWrapper.body;

			// now let response wrapper send response to the output stream:
			responseWrapper.buffering = false;

			var response = new Response(responseBody);
			response.status = responseWrapper.status;
			response.contentType = responseWrapper.contentType;
			return response;
		}
		else
		{
			return new Response("Failed to forward request to: " + springRequestPath);
		}
	}
	else {
		// This is not a matching request - let the caller know by simply returning null:
		return null;
	}
};


/**
 * Capture the output of running the servlet filter chain.
 *
 * @param {Request} the request to be processed
 * @returns {Response} the generated output as a JSGI Response
 */
exports.runFilterChain = function(request, capture) {

	var response;
	var servletRequest = request.env.servletRequest;
	var servletResponse = request.env.servletResponse;

	// capturing of spring's output can also be prevented
	// with url param or request attribute:

	var captureParam = request.params["springcapture"];
	var captureAttr = request.springcapture;
	if(
		(typeof capture !== "undefined" && !capture)
		||
		((typeof captureParam !== "undefined") &&
			(captureParam === "false" ||
			captureParam === "no" ||
			captureParam === "none"))
		||
		((typeof captureAttr !== "undefined") &&
		!captureAttr)
	) {
		// Delegate the response generation to the filter chain:
		log.info("running filter chain");
		runFilter(servletRequest, servletResponse);
		log.info("running filter chain");

		// It has streamed it's output directly to the client:
		response = new Response("");
		response.headers = { 'X-JSGI-Skip-Response': true };
	}
	else {
		// Capture the output of the filter chain:
		var responseWrapper = captureFilter(servletRequest,
											servletResponse);
		var responseBody = responseWrapper.body;
		response = new Response(responseBody);
		response.status = responseWrapper.status;
		response.contentType = responseWrapper.contentType;
	}

	return response;
};

// This function is broken out just so the profiler
// will show the time of *just* running the filter chain:
function runFilter(servletRequest, servletResponse) {
	RingoJsgiFilter.runFilterChain(servletRequest, servletResponse);
}

//This function is broken out just so the profiler
//will show the time of *just* running the filter chain:
function captureFilter(servletRequest, servletResponse) {
	return RingoJsgiFilter.captureFilterChain(servletRequest, servletResponse);
}

/**
 * Parse the provided json string (otherwise cause the standard evolve
 * JSON response to be generated if it's invalid JSON).
 * @param {String} a string containing JSON.
 * @returns {Object} the javascript JSON Object
 */
exports.jsonParse = function(jsonString) {

	var jsonRequestBody;

	try {
		jsonRequestBody = JSON.parse(jsonString);
	} catch (error) {
		// We throw an error which can/will get caught by the jsonerror middleware
		// (assuming they've configured it in their middleware chain).
		throw {type: "invalid_json", message: "passed in json is not valid: " + error.message};
	}

	return jsonRequestBody;
}

/**
 * If the provided string is JSON, nicely format it, otherwise return
 * it unmodified.
 * @param (String) a string of text possibly JSON
 * @returns (String) a formatted JSON string if JSON
 **/
exports.formatJsonIfJson = function(maybeJsonStr) {
	var formatted = maybeJsonStr;
	if(maybeJsonStr && maybeJsonStr.indexOf('{') !== -1) {
		try {
			var json = JSON.parse(maybeJsonStr);
			formatted = JSON.stringify(json,null,4);
		} catch (error) {
			// this wasn't JSON after all (just return unmodified)
		}
	}
	return formatted;
}
