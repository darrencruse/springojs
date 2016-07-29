/**
 * @fileOverview Middleware for catching unhandled request
 * exceptions and forwarding those on to spring.
 *
 * This ensures that no matter how the request is "routed" we will
 * forward to spring anything that hasn't been explicitly handled
 * in ringo with an app.get/app.post/etc. type of handler.
 *
 * @example
 * app.configure("notfound", "error", "jsonerror", "captureunhandledrequest");
 */

var utils = require("springoutils");
var {Response} = require('ringo/webapp/response');
var log = require("ringo/logging").getLogger("captureunhandledrequest");

/**
 * Stick middleware forwarding unhandled requests to the admin side Spring request dispatcher.
 * @param {Function} next the wrapped middleware chain
 * @param {Object} app the Stick Application object
 * @returns {Function} a JSGI response object
 */
exports.middleware = function captureunhandledrequest(next, app) {

	// defaults below work for portal api only
	// (i.e. need to override these defaults for everywhere else)
	app.captureunhandledrequest = {
			from: /\/.*\/api\//,
			to: '\/_api\/'
	};

	return function captureunhandledrequest(request) {
		try {

			return next(request);

		} catch (unhandledReqError if unhandledReqError.notfound === true) {

			var resp = utils.capturefromspringdispatcher(request,
												app.captureunhandledrequest.from,
												app.captureunhandledrequest.to);

			// if we got a response
			if(resp) {
				// return whatever spring returned
				return resp;
			}
			else {
				// otherwise throw and let the "notfound" middleware handle it.
				throw unhandledReqError;
			}
		}
	}

};
