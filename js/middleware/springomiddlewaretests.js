var {Application} = require("stick");

/**
* The following are a series of tests for springo middleware.
*
* To run them, include code like you see below in your app's module (e.g. in it's
* config.js file):
*
*    var {Application} = require("stick");
*    var app = exports.app = Application();
*    app.configure("route");
*
*    var tests = require("springomiddlewaretests");
*    //tests.testSimpleForward(app);
*    //tests.testModifyServletRequest(app);
*    //tests.testModifyRequestParams(app);
*    //tests.testModifyRequestBody(app);
*    //tests.testModifyServletResponse(app);
*    tests.testModifyResponseBody(app);
*
*    // Default to forwarding everything we haven't intercepted above:
*    var defaultToForwarding = new Application();
*    defaultToForwarding.configure("notfound", "error", "jsonerror", "forwardtospringdispatcher");
*    app.get("/*", defaultToForwarding);
*    app.post("/*", defaultToForwarding);
*
* To run one of the tests, uncomment one of the test functions you see commented
* above and hit a test url from your browser.
*
* Note the only reason you can't uncomment all the test functions at once is that we've
* routed the same urls to a number of these functions so they will conflict with one
* another if they're all commented at once.
*
**/

exports.testSimpleForwardChain = getSimpleForwardTestChain();
exports.testModifyServletRequestChain = getModifyServletRequestTestChain();
exports.testModifyRequestParamsChain = getModifyRequestParamsTestChain();
exports.testModifyRequestBodyChain = getModifyRequestBodyTestChain();
exports.testModifyServletResponseChain = getModifyServletResponseTestChain();
exports.testModifyResponseBodyChain = getModifyResponseBodyTestChain();

// Test a simple forward:
function getSimpleForwardTestChain() {
	var test = new Application();
	test.configure("notfound", "error", "jsonerror", "forwardtospringdispatcher");
	return test;
}

// Modify the servlet request object directly.
//
// This test modifies the "If-Modified-Since" header.
//
// Normally if you hit this url two times in a row, the first time will generate the data and return
// with a status of 200, and the second time it detects the data hasn't been modified and returns status 304.
//
// You can observe the behavior of overriding the If-Modified-Since header by setting the date way in the
// past (the 304 never comes), or way in the future (the 304 always comes).
//
function getModifyServletRequestTestChain() {
	var test = new Application();
	test.configure("notfound", "error", "jsonerror","modifyservletrequest","forwardtospringdispatcher");
	test.modifyservletrequest = function(servletRequest) {
		servletRequest.setHeader("If-Modified-Since", "Sat, 29 Oct 1994 19:43:31 GMT");
		return servletRequest;
	};
	return test;
}

// Override a query parameter:
function getModifyRequestParamsTestChain() {
	var test = new Application();
	test.configure("notfound", "error", "jsonerror", "modifyrequestparams","forwardtospringdispatcher");
	test.modifyrequestparams = { sessionId: 'modifiedsessionid', 'apikey': 'modifiedapikey' };
	return test;
}


// Modify the json request body...
// note we assume the test url posts json with an array of "users".
function getModifyRequestBodyTestChain() {
	var test = new Application();
	test.configure("notfound", "error", "jsonerror", "modifyrequestbody","forwardtospringdispatcher");
	test.modifyrequestbody = function(json) {
		json.users[0] = "Fred";
		return json;
	};
	return test;
}

// Modify the servlet response from Spring directly:
function getModifyServletResponseTestChain() {
	var test = new Application();
	test.configure("notfound", "error", "jsonerror", "modifyservletresponse","forwardtospringdispatcher");
	test.modifyservletresponse = function(servletResponse) {
		if(servletResponse.status == 201) {
			servletResponse.status = 302;
		}
	};
	return test;
}

// Modify the JSON response.
// note we assume the test url returns json with an array of "users".
//
function getModifyResponseBodyTestChain() {
	var test = new Application();
	test.configure("notfound", "error", "jsonerror", "modifyresponsebody","forwardtospringdispatcher");
	test.modifyresponsebody = function(json) {
		json.users[0] = "Fred";
	};
	return test;
}
