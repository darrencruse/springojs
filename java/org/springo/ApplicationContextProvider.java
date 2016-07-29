package org.springo;

import javax.servlet.ServletContext;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.context.ServletContextAware;

public class ApplicationContextProvider implements ApplicationContextAware, ServletContextAware {
	private static ApplicationContext appCtx = null;
	private static ServletContext servletCtx = null;

    public static ApplicationContext getApplicationContext() {
        return appCtx;
    }

	public void setApplicationContext(ApplicationContext ctx)
			throws BeansException {
		// Assign the ApplicationContext into a static method
		this.appCtx = ctx;
	}

	public static ServletContext getServletContext() {
		return servletCtx;
	}

	public void setServletContext(ServletContext ctx) {
		servletCtx = ctx;
	}

	/**
	 * Get the named bean from the Spring ApplicationContext,
	 * otherwise a BeansException is thrown.
	 *
	 * Note:  This is a thin wrapper on Spring's own
	 *   ApplicationContext.getBean(name) method.  It's been created
	 *   to workaround a problem where Rhino/Ringo was throwing
	 *   an exception (for some unknown reason) if
	 *   ApplicationContext.getBean(name) was called from javascript.
	 *   Without this, we could only get beans from javascript
	 *   using the ApplicationContext.getBean(name, class) overload
	 *   of getBean().
	 *
	 * @param name
	 * @return
	 */
	public static Object getBean(String name) {
		return appCtx.getBean(name);
	}
}
