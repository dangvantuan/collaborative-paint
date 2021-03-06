/**
 * 
 */
package com.acme.collpaint.client.page;

import java.util.HashMap;
import java.util.Map;

import com.acme.collpaint.client.Line;
import com.acme.collpaint.client.LineUpdate;
import com.acme.collpaint.client.page.CollPaintPresenter.UpdatesSender;
import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.canvas.dom.client.CssColor;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;

/**
 * <dl>
 * <dt>Project:</dt> <dd>collaborative-paint</dd>
 * <dt>Package:</dt> <dd>com.acme.collpaint.client.page</dd>
 * </dl>
 *
 * <code>CollPaintView</code>
 *
 * <p>Description</p>
 *
 * @author Ulric Wilfred <shaman.sir@gmail.com>
 * @date May 22, 2011 6:35:02 PM 
 *
 */
public class CollPaintView extends Composite implements CollPaintPresenter.Display {
    
    interface CollPaintViewUiBinder extends UiBinder<Widget, CollPaintView> { }   
    private static CollPaintViewUiBinder uiBinder = 
                                 GWT.create(CollPaintViewUiBinder.class);
    
    @UiField Label loginStatus;
    @UiField Button logout;
    
    @UiField Button clearCanvas;
    @UiField FlowPanel colors;
    @UiField ListBox thicknessBox;
    
    @UiField FlowPanel canvasHolder;
    
    private final Map<Color, Button> colorButtons = new HashMap<Color, Button>();
    private Color curColor = Color.BLACK;
    private Thickness curThickness = Thickness.NORMAL;
    
    private String currentUser;
    private Line currentLine;    
    private int lastLineId = 0;    
    // key is: "<username>/<lineId>"
    private Map<String, Line> drawnLines = new HashMap<String, Line>();
    
    private Canvas canvas = null;
    private Canvas backBuffer = null;
    private final CssColor redrawColor = CssColor.make("rgba(200,200,200,0.8)");
    
    private int canvasWidth = 0;
    private int canvasHeight = 0;    
    
    private UpdatesSender sender = null;
    
    public enum Thickness { NORMAL(0.6),
                            THIN(0.4),
                            FAT(0.8),
                            VERY_THIN(0.2),
                            VERY_FAT(1.0);    
        public final double value;        
        private Thickness(double value) { this.value = value; } 
    };
    
    public enum Color { BLACK(0.0, 0.0, 0.0),
                        WHITE(1.0, 1.0, 1.0),
                        RED(1.0, 0.0, 0.0), 
                        BLUE(0.0, 1.0, 0.0),
                        GREEN(0.0, 0.0, 0.1),
                        LIGHT_GRAY(0.66, 0.66, 0.66),
                        DARK_GRAY(0.33, 0.33, 0.33);    
        public final double r;
        public final double g;
        public final double b;
        private Color(double r, double g, double b) { 
                    this.r = r; this.g = g; this.b = b; }
    };    

    public CollPaintView() {
        super();
        initWidget(uiBinder.createAndBindUi(this));
        setupComponents();
    }
    
    protected void setupComponents() {
        updateLoginStatus(null);        
        enableControls(false);
        
        for (Thickness thickness: Thickness.values()) {
            thicknessBox.addItem(thickness.name() + " (" + thickness.value + ")", 
                                 thickness.name());
        }
        
        thicknessBox.addChangeHandler(new ChangeHandler() {
            @Override public void onChange(ChangeEvent event) {
                curThickness = 
                    Thickness.valueOf(
                            thicknessBox.getValue(thicknessBox.getSelectedIndex()));
            }
        });
        
        for (final Color color: Color.values()) {
            final Button colorButton = new Button();
            colorButton.setEnabled(false);
            colorButton.setText(color.name()); // TODO: set background color
            colorButtons.put(color, colorButton);
            colors.add(colorButton);
            
            colorButton.addClickHandler(new ClickHandler() {
                @Override public void onClick(ClickEvent event) {
                    colorButtons.get(curColor).setEnabled(true);
                    curColor = color;
                    colorButtons.get(curColor).setEnabled(false);
                }
            });
        }
    }
    
    @Override
    public void updateLoginStatus(String username) {
        currentUser = username;
        loginStatus.setText(username != null ? ("logged in as " + username) : "not logged in");
        logout.setEnabled(username != null);
    }

    @Override
    public void enableControls(boolean enable) {
        clearCanvas.setEnabled(enable);
        thicknessBox.setEnabled(enable);
        for (Button button: colorButtons.values()) {
            button.setEnabled(enable); 
        }
        
        if (enable) colorButtons.get(curColor).setEnabled(false);        
    }
    
    @Override
    public boolean prepareCanvas() {
        canvas = Canvas.createIfSupported();
        backBuffer = Canvas.createIfSupported();
        
        if (canvas == null) {
            canvasHolder.add(new Label("Sorry, your browser doesn't support the HTML5 Canvas element"));
            return false;
        }
        
        Window.addResizeHandler(new ResizeHandler() {
            @Override public void onResize(ResizeEvent event) {
                updateCanvasSize();                
            }
        });
        
        canvasHolder.add(canvas);

        updateCanvasSize();
        
        canvas.addMouseDownHandler(new MouseDownHandler() {
            @Override public void onMouseDown(MouseDownEvent event) {
                sender.lineUpdated(
                    startNewLine(getWidthPercent(event),
                                 getHeightPercent(event))
                );
                redrawCanvas(canvasWidth, canvasHeight);
                event.preventDefault();
            }
        });
        
        canvas.addMouseMoveHandler(new MouseMoveHandler() {
            @Override public void onMouseMove(MouseMoveEvent event) {
                if (currentLine == null) return;
                sender.lineUpdated(
                    updateCurrentLine(getWidthPercent(event),
                                      getHeightPercent(event))
                );
                redrawCanvas(canvasWidth, canvasHeight);
                event.preventDefault();                
            }
        });
        
        canvas.addMouseUpHandler(new MouseUpHandler() {
            @Override public void onMouseUp(MouseUpEvent event) {
                if (currentLine == null) return;                
                sender.lineFinished(
                    finishCurrentLine(getWidthPercent(event),
                                      getHeightPercent(event))                                      
                );
                redrawCanvas(canvasWidth, canvasHeight);                
                event.preventDefault();                
            }
        });
        
        canvas.addMouseOutHandler(new MouseOutHandler() {
            @Override public void onMouseOut(MouseOutEvent event) {
                if (currentLine == null) return;                
                sender.lineFinished(
                    finishCurrentLine(getWidthPercent(event),
                                      getHeightPercent(event))    
                );
                redrawCanvas(canvasWidth, canvasHeight);
                event.preventDefault();                
            }
        });        
        
        return true;
    }
    
    protected void updateCanvasSize() {        
        canvasWidth = Window.getClientWidth(); // canvasHolder.getOffsetWidth()
        canvasHeight = (int)(Window.getClientHeight() * 0.65); // 65% of height        
        
        canvas.setWidth(canvasWidth + "px");
        canvas.setCoordinateSpaceWidth(canvasWidth);        
        canvas.setHeight(canvasHeight + "px");
        canvas.setCoordinateSpaceHeight(canvasHeight);
        
        Log.debug("catched resize, will redraw (" + canvasWidth + "x" +
                                                    canvasHeight + ")");        
        
        redrawCanvas(canvasWidth, canvasHeight);
    }
    
    protected void redrawCanvas(int width, int height) {
        if (canvas == null) return; 
        
        final Context2d context = canvas.getContext2d();
        //final Context2d bufContext = backBuffer.getContext2d();
        
        context.setFillStyle(redrawColor);
        context.fillRect(0, 0, width, height);
        
        if (currentLine != null) {
            Line.draw(context, currentLine, width, height);
        }
        
        for (Line line: drawnLines.values()) {
            Line.draw(context, line, width, height);            
        }              
        
        /* bufContext.setFillStyle(redrawColor);
        bufContext.fillRect(0, 0, width, height);
        
        if (currentLine != null) {
            Line.draw(bufContext, currentLine, width, height);
        }
        
        for (Line line: drawnLines.values()) {
            Line.draw(bufContext, line, width, height);            
        }
        
        context.drawImage(bufContext.getCanvas(), 0, 0); */
    }
    
    @Override
    public void drawUpdate(LineUpdate update) {
        Log.debug("Will draw this: " + update.info());
        drawnLines.put(update.getAuthor() + "/"
                       + update.getLineId(), 
                       update.getSource());
        redrawCanvas(canvasWidth, canvasHeight);
    }    

    @Override
    public void setUpdatesSender(UpdatesSender sender) {
        this.sender = sender;
    }
    
    @Override
    public HasClickHandlers getLogoutButton() { return logout; }
    
    private double getWidthPercent(MouseEvent<?> source) {
        return (double)source.getRelativeX(canvas.getElement()) / (double)canvasWidth;        
    };
    
    private double getHeightPercent(MouseEvent<?>  source) {
        return (double)source.getRelativeY(canvas.getElement()) / (double)canvasHeight;
    };    
    
    private Line startNewLine(double startX, double startY) {        
        if (currentLine != null) return null;
        lastLineId += 1;
        final Line newLine = new Line();
        newLine.setAuthor(currentUser);
        newLine.setLineId(lastLineId);
        newLine.setColor(curColor.r, curColor.g, curColor.b);
        newLine.setThickness(curThickness.value);
        newLine.setStart(startX, startY);
        newLine.setEnd(startX, startY);
        Log.debug("Started line " + newLine.info());        
        currentLine = newLine;
        return currentLine;
    }

    private Line updateCurrentLine(double endX, double endY) {
        if (currentLine == null) return null;
        currentLine.setEnd(endX, endY);
        Log.debug("Updated line " + currentLine.info());
        return currentLine;
    }

    private Line finishCurrentLine(double endX, double endY) {
        if (currentLine == null) return null;
        final Line finishedLine = currentLine;
        finishedLine.setEnd(endX, endY);
        if (currentUser != null) {
            drawnLines.put(currentUser + "/" + finishedLine.getLineId(), 
                           finishedLine);
        }        
        currentLine = null;
        Log.debug("Finished line " + finishedLine.info());
        return finishedLine;
    }

    @Override
    public void forgetData() {
        drawnLines.clear();
        redrawCanvas(canvasWidth, canvasHeight);
    }

    @Override
    public HasClickHandlers getClearButton() {
        return clearCanvas;
    }
    
}
