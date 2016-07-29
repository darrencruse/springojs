// DEPRECATED - forwardtospringdispatcher is now treated as an "action" not a "middleware".
// See function added in communutil.js

/**
 * @fileOverview Middleware for forwarding a request on to Spring, and returning
 * what Spring returns.
 *
 * If desired, you can change the (regex) matching the url with the
 * `app.forwardtospringdispatcher.from` property, and the (string) replacing it
 * with the `app.forwardtospringdispatcher.to` property.
 *
 * @example
 * app.configure("notfound", "error", "jsonerror", "forwardtospringdispatcher");
 * app.forwardtospringdispatcher.from = /\/.*\/testapi\//;
 * app.forwardtospringdispatcher.to = '\/_testapi\/';
 */
var utils = require("springoutils");
var {Response} = require('ringo/webapp/response');

/**
 * Stick middleware forwarding to the Spring MVC api.
 * @param {Function} next the wrapped middleware chain
 * @param {Object} app the Stick Application object
 * @returns {Function} a JSGI middleware function
 */
exports.middleware = function forwardtospringdispatcher(next, app) {

    app.forwardtospringdispatcher = {
        from: /\/.*\/api\//,
        to: '\/_api\/'
    };

    return function forwardtospringdispatcher(request) {

        var resp = utils.forwardtospringdispatcher(request,
											app.forwardtospringdispatcher.from,
											app.forwardtospringdispatcher.to);
        if (resp) {
            return resp;
        } else {
            // This is not a request matching "from" - pass it down the chain:
            return next(request);
        }
    };
};
