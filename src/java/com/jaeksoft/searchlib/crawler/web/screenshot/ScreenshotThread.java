/**   
 * License Agreement for Jaeksoft OpenSearchServer
 *
 * Copyright (C) 2011 Emmanuel Keller / Jaeksoft
 * 
 * http://www.open-search-server.com
 * 
 * This file is part of Jaeksoft OpenSearchServer.
 *
 * Jaeksoft OpenSearchServer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Jaeksoft OpenSearchServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Jaeksoft OpenSearchServer. 
 *  If not, see <http://www.gnu.org/licenses/>.
 **/

package com.jaeksoft.searchlib.crawler.web.screenshot;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.concurrent.locks.ReentrantLock;

import javax.imageio.ImageIO;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.firefox.FirefoxDriver;

import com.jaeksoft.searchlib.config.Config;
import com.jaeksoft.searchlib.process.ThreadAbstract;

public class ScreenshotThread extends ThreadAbstract {

	private URL url;
	private byte[] data;
	private Dimension capture;
	private Dimension resize;
	private FirefoxDriver driver;
	private ScreenshotManager screenshotManager;
	private BufferedImage finalImage;

	private final ReentrantLock lock = new ReentrantLock();

	public ScreenshotThread(Config config, ScreenshotManager screenshotManager,
			URL url) {
		super(config, null);
		driver = null;
		this.url = url;
		data = null;
		finalImage = null;
		this.screenshotManager = screenshotManager;
		this.capture = screenshotManager.getCaptureDimension();
		this.resize = screenshotManager.getResizeDimension();
	}

	private final void initDriver() {
		lock.lock();
		try {
			driver = new FirefoxDriver();
		} finally {
			lock.unlock();
		}

	}

	private final BufferedImage scaleWidth(BufferedImage image) {
		BufferedImage scaledImage = new BufferedImage(capture.width,
				image.getHeight(), image.getType());
		Graphics2D graphics2D = scaledImage.createGraphics();
		graphics2D.drawImage(image, (capture.width - image.getWidth()) / 2, 0,
				image.getWidth(), image.getHeight(), null);
		graphics2D.dispose();
		return scaledImage;
	}

	private final BufferedImage scaleHeight(BufferedImage image) {
		BufferedImage scaledImage = new BufferedImage(image.getWidth(),
				capture.height, image.getType());
		Graphics2D graphics2D = scaledImage.createGraphics();
		graphics2D.drawImage(image, 0,
				(capture.height - image.getHeight()) / 2, image.getWidth(),
				image.getHeight(), null);
		graphics2D.dispose();
		return scaledImage;
	}

	private final BufferedImage extractSubImage(BufferedImage image) {
		int left = (image.getWidth() - capture.width) / 2;
		return image.getSubimage(left, 0, capture.width, capture.height);
	}

	private final BufferedImage resizeImage(BufferedImage image) {
		int type = (image.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB
				: BufferedImage.TYPE_INT_ARGB;
		BufferedImage ret = (BufferedImage) image;
		int w = image.getWidth();
		int h = image.getHeight();

		while (w != resize.width || h != resize.height) {
			if (w > resize.width) {
				w /= 2;
				if (w < resize.width)
					w = resize.width;
			}

			if (h > resize.height) {
				h /= 2;
				if (h < resize.height)
					h = resize.height;
			}

			BufferedImage tmp = new BufferedImage(w, h, type);

			Graphics2D g2 = tmp.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.drawImage(ret, 0, 0, w, h, null);
			g2.dispose();

			ret = tmp;
		}

		return ret;
	}

	@Override
	public void runner() throws Exception {
		try {
			initDriver();
			driver.get(url.toExternalForm());
			data = driver.getScreenshotAs(OutputType.BYTES);
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
			if (image.getWidth() < capture.width)
				image = scaleWidth(image);
			if (image.getHeight() < capture.height)
				image = scaleHeight(image);
			image = extractSubImage(image);
			if (resize.width != capture.width
					&& resize.height != capture.height)
				image = resizeImage(image);
			finalImage = image;
			screenshotManager.store(url, image);
		} finally {
			release();
		}
	}

	public BufferedImage getImage() {
		return finalImage;
	}

	@Override
	public void release() {
		lock.lock();
		try {
			if (driver == null)
				return;
			driver.quit();
			driver = null;
		} finally {
			lock.unlock();
		}
	}

}
