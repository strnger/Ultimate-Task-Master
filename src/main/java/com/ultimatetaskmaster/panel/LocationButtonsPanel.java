package com.ultimatetaskmaster.panel;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import com.ultimatetaskmaster.data.LocationCluster;
import net.runelite.client.ui.FontManager;

/**
 * Panel that displays location suggestion buttons for a task.
 * Extracted from PlanItemPanel for reusability.
 */
public class LocationButtonsPanel extends JPanel {
    private static final Color PINNED_COLOR = new Color(255, 140, 0);
    private static final Color SUGGESTION_COLOR = new Color(100, 100, 100);
    private static final Color SUGGESTION_HOVER = new Color(70, 70, 70);
    
    public LocationButtonsPanel(String taskName, List<LocationCluster> locations,
                                BiConsumer<String, LocationCluster> onPinCallback,
                                Integer pinnedX, Integer pinnedY) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(new EmptyBorder(3, 7, 3, 7));
        
        if (locations == null || locations.isEmpty()) {
            return;
        }
        
        // Location buttons row
        JPanel locationRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        locationRow.setOpaque(false);
        
        JLabel locLabel = new JLabel("Locations: ");
        locLabel.setFont(FontManager.getRunescapeSmallFont());
        locLabel.setForeground(Color.GRAY);
        locationRow.add(locLabel);
        
        int shown = 0;
        int maxShown = 5;
        
        for (LocationCluster cluster : locations) {
            if (shown >= maxShown) {
                break;
            }
            
            boolean isPinned = pinnedX != null && pinnedY != null
                && Math.abs(pinnedX - cluster.getX()) < 2
                && Math.abs(pinnedY - cluster.getY()) < 2;
            
            JButton btn = new JButton(String.format("[%d,%d (%d)]",
                cluster.getX(), cluster.getY(), cluster.getCount()));
            btn.setFont(FontManager.getRunescapeSmallFont());
            btn.setForeground(Color.WHITE);
            btn.setBackground(isPinned ? PINNED_COLOR : SUGGESTION_COLOR);
            btn.setBorder(new EmptyBorder(2, 4, 2, 4));
            btn.setFocusPainted(false);
            btn.setToolTipText(String.format("Click to pin this location (%d,%d)",
                cluster.getX(), cluster.getY()));
            
            if (!isPinned) {
                btn.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        btn.setBackground(SUGGESTION_HOVER);
                    }
                    @Override
                    public void mouseExited(MouseEvent e) {
                        btn.setBackground(SUGGESTION_COLOR);
                    }
                });
            }
            
            btn.addActionListener(e -> {
                if (onPinCallback != null) {
                    onPinCallback.accept(taskName, cluster);
                }
            });
            
            locationRow.add(btn);
            shown++;
        }
        
        if (locations.size() > maxShown) {
            JLabel moreLabel = new JLabel(String.format("+%d more", locations.size() - maxShown));
            moreLabel.setFont(FontManager.getRunescapeSmallFont());
            moreLabel.setForeground(Color.GRAY);
            locationRow.add(moreLabel);
        }
        
        add(locationRow);
        
        // Pinned location indicator
        if (pinnedX != null && pinnedY != null) {
            JPanel pinnedRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
            pinnedRow.setOpaque(false);
            
            JLabel pinnedLabel = new JLabel(String.format("📍 Pinned: %d,%d", pinnedX, pinnedY));
            pinnedLabel.setFont(FontManager.getRunescapeSmallFont());
            pinnedLabel.setForeground(PINNED_COLOR);
            pinnedRow.add(pinnedLabel);
            
            add(pinnedRow);
        }
    }
}
