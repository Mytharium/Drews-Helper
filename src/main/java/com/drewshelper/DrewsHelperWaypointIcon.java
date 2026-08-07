package com.drewshelper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class DrewsHelperWaypointIcon
{
    public static final int DEFAULT_SIZE = 26;

    private DrewsHelperWaypointIcon()
    {
    }

    public static BufferedImage createImage(int waypointNumber, Color color)
    {
        return createImage(waypointNumber, color, DEFAULT_SIZE);
    }

    public static BufferedImage createImage(int waypointNumber, Color color, int imageSize)
    {
        BufferedImage image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(0, 0, 0, 150));
            graphics.fillOval(2, 3, imageSize - 5, imageSize - 5);
            graphics.setColor(color);
            graphics.fillOval(3, 2, imageSize - 7, imageSize - 7);
            graphics.setStroke(new BasicStroke(Math.max(1, imageSize / 13)));
            graphics.setColor(Color.BLACK);
            graphics.drawOval(3, 2, imageSize - 7, imageSize - 7);

            String label = Integer.toString(waypointNumber);
            graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, Math.max(11.0f, imageSize * 0.54f)));
            FontMetrics metrics = graphics.getFontMetrics();
            int textX = (imageSize - metrics.stringWidth(label)) / 2;
            int textY = ((imageSize - metrics.getHeight()) / 2) + metrics.getAscent() - 1;
            graphics.setColor(readableTextColor(color));
            graphics.drawString(label, textX, textY);
        }
        finally
        {
            graphics.dispose();
        }
        return image;
    }

    static Color readableTextColor(Color color)
    {
        int luminance = (int) ((0.299 * color.getRed()) + (0.587 * color.getGreen()) + (0.114 * color.getBlue()));
        return luminance < 110 ? Color.WHITE : Color.BLACK;
    }
}
