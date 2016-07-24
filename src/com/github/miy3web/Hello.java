package com.github.miy3web;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Hello extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private boolean _bValue;
	private int _iSort;

	public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		int liSort;
		response.setContentType(ServletProperties.replaceSystemValue("text/html; charset=${file.encoding}"));
		PrintWriter lOut = response.getWriter();

		try {
			liSort = Integer.parseInt(request.getParameter("sort"));
		} catch (Exception ex) {
			liSort = 1;
		}
		String lsProp = "servlet.properties";
		Properties lPropServlet = ServletProperties.loadProperties(getServletContext(), lsProp);
		lOut.println("<html><head>" + getStyle(lPropServlet.getProperty("style.Hello")) + "</head><body>");
		lOut.println("<pre>パラメータ\r\n\tsort\tソート 1=キー昇順 -1=キー降順 2=値昇順 -2=値降順 (既定値:1)\r\n</pre><br>");

		listMap(lOut, lPropServlet, "サーブレットプロパティ " + lsProp, liSort);
		lsProp = (lPropServlet != null) ? lPropServlet.getProperty("app.properties") : null;
		if (lsProp != null) {
			Properties lProp = ServletProperties.loadProperties(getServletContext(), lsProp);
			listMap(lOut, lProp, "アプリケーションプロパティリスト " + lsProp, liSort);
		}
		listMap(lOut, System.getProperties(), "プロパティリスト System.getProperties()", liSort);
		listMap(lOut, System.getenv(), "環境変数リスト System.getenv()", liSort);
		lOut.println("</body></html>");
	}

	private String getStyle(String asStyle) {
		StringBuilder sb = new StringBuilder();
		sb.append("<style>");
		if (asStyle == null || "".equals(asStyle)) {
			sb.append("table {border-collapse: collapse;}");
			sb.append("caption {background-color: #47c3d3; color:white;}");
			sb.append("th {background-color: #5f6062; color:white;}");
		} else {
			sb.append(asStyle);
		}
		sb.append("</style>");
		return sb.toString();
	}

	private void listMap(PrintWriter out, Map<?, ?> aMap, String asCaption, int aiSort) {
		out.println("<table border='1'><caption>" + asCaption + "</caption><tr><th>キー</th><th>値</th></tr>");
		if (aMap != null) {
			List<Map.Entry<?, ?>> list = sortedMapList(aMap, aiSort);
			for (Map.Entry<?, ?> e : list) {
				String k = e.getKey().toString();
				String v = e.getValue().toString();
				if (k.matches(".*passwd|.*password")) {
					v = v.replaceAll(".", "X");
				}
				out.println("<tr><td>" + k + "</td><td>" + v + "</td></tr>");
			}
		}
		out.println("</table>");
	}

	private List<Map.Entry<?, ?>> sortedMapList(Map<?, ?> aMap, int aiSort) {
		List<Map.Entry<?, ?>> list = new ArrayList<>(aMap.entrySet());
		if (aiSort != 0) {
			this._bValue = ((aiSort & 0x1) == 0);
			this._iSort = aiSort;
			Collections.sort(list, new Comparator<Map.Entry<?, ?>>() {
				public int compare(Map.Entry<?, ?> p1, Map.Entry<?, ?> p2) {
					return Hello.this._bValue
							? (Hello.this._iSort * p1.getValue().toString().compareTo(p2.getValue().toString()))
							: (Hello.this._iSort * p1.getKey().toString().compareTo(p2.getKey().toString()));
				}
			});
		}
		return list;
	}
}
