/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.webinv.image;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ImagingOpException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author Krzysztof Kardasz
 */
public class Image {
    private final Logger logger = LogManager.getLogger(this.getClass());
    private int width;
    private int height;

    private File file;
    private BufferedImage src;

    public enum Rotation {
        CW_90,
        CW_180,
        CW_270,
        FLIP_HORIZONTAL,
        FLIP_VERTICAL
    }

    public Image(String filepath) throws IOException {
        this.file = new File(filepath);
        this.src  = ImageIO.read(new FileInputStream(this.file));
        this.width = src.getWidth();
        this.height = src.getHeight();
    }

    public Image(File file) throws IOException {
        this.file = file;
        this.src  = ImageIO.read(new FileInputStream(this.file));
        this.width = src.getWidth();
        this.height = src.getHeight();
    }

    public int getWidth() {
        return src.getWidth();
    }

    public int getHeight() {
        return  src.getHeight();
    }

    public void save () throws IOException {
        int pos = file.getPath().lastIndexOf('.');
        if (pos != -1) {
            String fileType = file.getPath().substring(pos+1);
            ImageIO.write(src, fileType, file);
        }
    }

    public void saveTo (File file) throws IOException {
        int pos = file.getPath().lastIndexOf('.');
        if (pos != -1) {
            String fileType = file.getPath().substring(pos+1);
            ImageIO.write(src, fileType, file);
        }
    }

    public void rotate (Rotation rotation) throws IllegalArgumentException, ImagingOpException {
        int type = (src.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;

        int w = src.getWidth();
        int h = src.getHeight();

        AffineTransform tx = new AffineTransform();

        switch (rotation) {
            case CW_90:
                w = src.getHeight();
                h = src.getWidth();

                tx.translate(w, 0);
                tx.rotate(Math.toRadians(90));

                break;

            case CW_270:
                w = src.getHeight();
                h = src.getWidth();

                tx.translate(0, h);
                tx.rotate(Math.toRadians(-90));
                break;

            case CW_180:
                tx.translate(w, h);
                tx.rotate(Math.toRadians(180));
                break;

            case FLIP_HORIZONTAL:
                tx.translate(w, 0);
                tx.scale(-1.0, 1.0);
                break;

            case FLIP_VERTICAL:
                tx.translate(0, h);
                tx.scale(1.0, -1.0);
                break;
        }


        BufferedImage result = new BufferedImage(w, h, type);
        Graphics2D g2 = result.createGraphics();
        g2.drawImage(src, tx, null);
        g2.dispose();


        src.flush();
        src = result;

        this.width = result.getWidth();
        this.height = result.getHeight();
    }

    public void crop (int x1, int y1, int width, int height) {
        if (x1 == 0 && y1 == 0 && width == this.width && height == this.height) {
            return ;
        }

        if ((width+x1) > this.width || (height+y1) > this.height) {
            return ;
        }

        BufferedImage result = src.getSubimage(x1, y1, width, height);

        src.flush();
        src = result;

        this.width = result.getWidth();
        this.height = result.getHeight();
    }

    public void resizeTo (int width, int height) {
        if (width == this.width && height == this.height) {
            return ;
        }

        int x1 = 0;
        int y1 = 0;

        if (this.width < width) {
            x1 = (width - this.width) / 2;
        }

        if (this.height < height) {
            y1 = (height - this.height) / 2;
        }

        int x2 = this.width + x1;
        int y2 = this.height + y1;

        BufferedImage tmp = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = tmp.createGraphics();
        g2.setBackground(Color.BLACK);
        g2.clearRect(0, 0, width, height);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        g2.drawImage(src, x1, y1, x2, y2, 0, 0, this.width, this.height, null);
        g2.dispose();

        src.flush();
        src = tmp;

        this.width = src.getWidth();
        this.height = src.getHeight();
    }

    public void resizeFitTo (int width, int height) {
        if (width == this.width && height == this.height) {
            return ;
        }

        if (width > this.width || height > this.height) {
            return ;
        }

        int x1 = 0;
        int y1 = 0;

        int targetX = Math.round((this.height * width) / height);
        int targetY = Math.round((this.width * height) / width);

        if (targetX < this.width) {
            x1 = (int)Math.floor((this.width-targetX)/2);
            targetY = this.height;
        } else if (targetY < this.height) {
            y1 = (int)Math.floor((this.height-targetY)/2);
            targetX = this.width;
        }

        crop(x1, y1, targetX, targetY);
        resize(width, height);
    }

    public void resizeToWidthHeight (int width, int height) {
        if (width < this.width) {
            resizeToWidth(width);
        }

        if (height < this.height) {
            resizeToHeight(height);
        }
    }


    public void resizeToWidth (int width) {
        resize(width, Math.round((width * this.height) / this.width));
    }


    public void resizeToHeight (int height) {
        resize(Math.round((this.width * height) / this.height), height);
    }
    
    public void resize (int width, int height) {
        // https://today.java.net/pub/a/today/2007/04/03/perils-of-image-getscaledinstance.html

        if (this.width == width && this.height == height) {
            return ;
        }

        if (width > this.width || height > this.height) {
            // throw new exception
            return ;
        }

        int type = (src.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        int w = src.getWidth();
        int h = src.getHeight();

        do {
            if (w > width) {
                w /= 2;
                if (w < width) {
                    w = width;
                }
            }

            if (h > height) {
                h /= 2;
                if (h < height) {
                    h = height;
                }
            }

            BufferedImage tmp = new BufferedImage(w, h, type);
            Graphics2D g2 = tmp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.drawImage(src, 0, 0, w, h, null);
            g2.dispose();

            src.flush();
            src = tmp;
        } while (w != width || h != height);

        this.width = src.getWidth();
        this.height = src.getHeight();
    }
}
