// canvas dimensions
var kWidth = 640;
var kHeight= 480;

function supports_canvas() {
    return !!document.createElement('canvas').getContext;
}

function supports_canvas_text() {
    if (!supports_canvas()) { return false; }
    var dummy_canvas = document.createElement('canvas');
    var context = dummy_canvas.getContext('2d');
    return typeof context.fillText == 'function';
}

// http://paulirish.com/2011/requestanimationframe-for-smart-animating/
// http://my.opera.com/emoller/blog/2011/12/20/requestanimationframe-for-smart-er-animating

// requestAnimationFrame polyfill by Erik Möller
// fixes from Paul Irish and Tino Zijdel
(function() {
    var lastTime = 0;
    var vendors = ['ms', 'moz', 'webkit', 'o'];
    for(var x = 0; x < vendors.length && !window.requestAnimationFrame; ++x) {
        window.requestAnimationFrame = window[vendors[x]+'RequestAnimationFrame'];
        window.cancelAnimationFrame = 
          window[vendors[x]+'CancelAnimationFrame'] || window[vendors[x]+'CancelRequestAnimationFrame'];
    }
 
    if (!window.requestAnimationFrame)
        window.requestAnimationFrame = function(callback, element) {
            var currTime = new Date().getTime();
            var timeToCall = Math.max(0, 16 - (currTime - lastTime));
            var id = window.setTimeout(function() { callback(currTime + timeToCall); }, 
              timeToCall);
            lastTime = currTime + timeToCall;
            return id;
        };
 
    if (!window.cancelAnimationFrame)
        window.cancelAnimationFrame = function(id) {
            clearTimeout(id);
        };
}());

function supports_html5_storage() {
    try {
        return 'localStorage' in window && window['localStorage'] !== null;
    } catch (e) {
        return false;
    }
}

// on the recommendation of HTML5: Up and Running
function getCursorPosition(el, ev) {
    var x, y;
    if (ev.pageX != undefined && ev.pageY != undefined) {
        x = ev.pageX;
        y = ev.pageY;
    } else {
        x = ev.clientX + document.body.scrollLeft + document.documentElement.scrollLeft;
        y = ev.clientY + document.body.scrollTop + document.documentElement.scrollTop;
    }

    x -= el.offsetLeft;
    y -= el.offsetTop;

    return {x: x, y: y};
}

function initMouseEvents(canvasElement, clickFcn, pickupFcn, dragFcn, dropFcn) {
    var in_range = false;
    var dragging = false;
    var last_point = {x:0, y:0};

    var handleMouseDrags = function (e, old_dragging) {
        var p = getCursorPosition(canvasElement, e);

        if (!old_dragging && dragging) {
            pickupFcn(p);
            last_point = p;
        }
        if (old_dragging && !dragging) {
            dropFcn(last_point);
        }
        if (old_dragging && dragging) {
            dragFcn(p);
            last_point = p;
        }
    };

    canvasElement.addEventListener("mousedown", function (e) {
        var old_dragging = dragging;

        if (in_range) {
            if (!clickFcn(getCursorPosition(canvasElement, e))) {
                // click didn't capture this mousedown
                dragging = true;
            }
        }

        handleMouseDrags(e, old_dragging);
    }, false);
    canvasElement.addEventListener("mouseup", function (e) {
        var old_dragging = dragging;

        if (in_range) {
            dragging = false;
        }

        handleMouseDrags(e, old_dragging);
    }, false);
    canvasElement.addEventListener("mousemove", function (e) {
        if (in_range && dragging) {
            handleMouseDrags(e, dragging);
        }
    }, false);
    canvasElement.addEventListener("mouseover", function (e) {
        in_range = true;
    }, false);
    canvasElement.addEventListener("mouseout", function (e) {
        var old_dragging = dragging;

        in_range = false;
        dragging = false;
        handleMouseDrags(e, old_dragging);
    }, false);
}

function addSimRenderer(backend, renderCanvasId, width, height) {
    var canvasElement;
    var renderContext;
    var animationStartTime;
    var lastFrameTime;
    var animateFcn;

    if (!supports_canvas()) {
        return;
    }

    canvasElement = document.createElement("canvas");
    canvasElement.id = renderCanvasId;
    document.body.appendChild(canvasElement);
    canvasElement.width = width
    canvasElement.height= height;
    renderContext = canvasElement.getContext("2d");

    animationStartTime = Date.now();
    lastFrameTime = animationStartTime;
    animateFcn = function (time) {
        var dt = time-lastFrameTime;

        var handle = window.requestAnimationFrame(animateFcn);

        if (!backend.render(canvasElement, renderContext, time-animationStartTime, dt)) {
            window.cancelAnimationFrame(handle);
        }

        lastFrameTime = time;
    }

    initMouseEvents(canvasElement,
        function (p) {
            if (typeof backend.mouseClick == 'function') {
                return backend.mouseClick(p);
            } else {
                return false;
            }
        },
        function (p) {
            if (typeof backend.mousePickup == 'function') {
                return backend.mousePickup(p);
            } else {
                return false;
            }
        },
        function (p) {
            if (typeof backend.mouseDrag == 'function') {
                return backend.mouseDrag(p);
            } else {
                return false;
            }
        },
        function (p) {
            if (typeof backend.mouseDrop == 'function') {
                return backend.mouseDrop(p);
            } else {
                return false;
            }
        }
    );

    window.requestAnimationFrame(animateFcn);
}

// the pre-built backend, main execution environment
function constBasicBackend() {
var that;
var arrow;

that = {};

arrow = {};

// init arrows (load from localStorage if possible)
var arrows = (function () {
    var arrows, arrowcount;

    if (!supports_html5_storage()) {
        return [];
    }

    arrowcount = parseInt(localStorage["test.arrowcount"]);
    if (isNaN(arrowcount)) {
        arrowcount = 0;
    }

    arrows = [];

    var parsePoints = function(s) {
        var a;
        if (typeof s != 'string') {
            return [];
        }
        
        a = s.split(',');
        if (a.length != 4) {
            return [];
        }

        return {start: {x: parseInt(a[0]),
                        y: parseInt(a[1])},
                  end: {x: parseInt(a[2]),
                        y: parseInt(a[3])}};
    };

    for (i = 0; i < arrowcount; i++) {
        arrows[i] = parsePoints(localStorage["test.arrow"+i]);
        window.console.log(localStorage["test.arrow"+i]);
        if (!('start' in arrows[i])) {
            return [];
        }
    }

    return arrows;
})();

var saveArrows = function () {
    var i;

    if (!supports_html5_storage()) {
        return;
    }

    localStorage["test.arrowcount"] = arrows.length;

    for (i = 0; i < arrows.length; i++) {
        localStorage["test.arrow"+i] = arrows[i].start.x +"," + arrows[i].start.y + "," + arrows[i].end.x + "," + arrows[i].end.y;
    }
};

var resetArrows = function () {
    arrows = [];
    localStorage.clear();
};

// public methods
that.mouseClick = function (p) {
    if (p.x < 40 && p.y < 40) {
        resetArrows();
        return true;
    } else {
        return false;
    }
};

that.mousePickup = function (p) {
    arrow = {start: p, end: p};
    return true;
};

that.mouseDrag = function (p) {
    arrow.end = p;
    return true;
};

that.mouseDrop = function (p) {
    arrow.end = p;
    arrows[arrows.length] = arrow;
    saveArrows();
    arrow = {}
    return true;
};

that.render = function(canvasElement, context, t, dt) {
    var w = canvasElement.width;
    var h = canvasElement.height;

    context.clearRect(0, 0, w, h);

    var drawArrow = function (arrow) {
        var sx, sy;
        var ex, ey;
        var dx, dy;
        var mag;
        var head_ang = Math.PI/4;

        sx = arrow.start.x;
        sy = arrow.start.y;
        ex = arrow.end.x;
        ey = arrow.end.y;
        dx = ex-sx;
        dy = ey-sy;
        mag = Math.sqrt(dx*dx+dy*dy);

        if (mag == 0) {
            dx = 0;
            dy = 0;
        }
        else {
            dx = -dx / mag * 10;
            dy = -dy / mag * 10;
        }
        
        context.beginPath();
        context.moveTo(sx+0.5, sy+0.5);
        context.lineTo(ex+0.5, ey+0.5);

        context.moveTo(ex+0.5-dy*Math.sin(head_ang)+dx*Math.cos(head_ang),
                       ey+0.5+dx*Math.sin(head_ang)+dy*Math.cos(head_ang));
        context.lineTo(ex+0.5, ey+0.5);
        context.lineTo(ex+0.5-dy*Math.sin(-head_ang)+dx*Math.cos(-head_ang),
                       ey+0.5+dx*Math.sin(-head_ang)+dy*Math.cos(-head_ang));
        context.stroke();
    };

    for (i = 0; i < arrows.length; i++) {
        drawArrow(arrows[i]);

        arrows[i].start.y += dt/1000*100;
        arrows[i].end.y += dt/1000*100;

        if (arrows[i].start.y > h && arrows[i].end.y > h) {
            var distdiff = Math.abs(arrows[i].end.y - arrows[i].start.y); 
            arrows[i].start.y -= kHeight + distdiff;
            arrows[i].end.y -= kHeight +   distdiff;
        }
    }

    // draw the currently-dragged arrow
    if ('start' in arrow) {
        // arrowhead
        drawArrow(arrow);
    }

    // keep runnning me
    return true;
};

that.getInterpreter = function () {
    var interpreterBackend;

    var scaledX = function (p) {
        return p.x/640*256;
    };
    var scaledY = function (p) {
        return p.y/480*256;
    };
    
    interpreterBackend = {}

    // make a dummy interpreter for now
    interpreterBackend.render = function (canvasElement, context, t, dt) {
        context.clearRect(0, 0, 256, 256);

        // render the last committed arrow
        if (arrows.length >= 1) {
            context.beginPath();
            context.moveTo(scaledX(arrows[arrows.length-1].start),scaledY(arrows[arrows.length-1].start));
            context.lineTo(scaledX(arrows[arrows.length-1].end),  scaledY(arrows[arrows.length-1].end));
            context.stroke();
        }

        return true;
    };

    return interpreterBackend;
};

return that;

}; // end of BasicBackend
