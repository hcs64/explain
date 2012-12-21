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

function AABB(_minx, _miny, _width, _height) {
    this.minx = _minx;
    this.miny = _miny;
    this.maxx = _minx + _width;
    this.maxy = _miny + _height
    this.width = _width;
    this.height = _height;

    this.contains = function(p) {
        return (p.x >= this.minx && p.y >= this.miny &&
                p.x <  this.maxx && p.y <  this.maxy);
    };
    this.move = function(x,y) {
        this.minx += x;
        this.miny += y;
        this.maxx += x;
        this.maxy += y;
    };
}

function constructButton(args) {
    var text, callback, aabb;

    var that;
    
    that = {};

    text        = args.text;
    aabb        = args.aabb;
    callback    = args.callback;

    that.contains = function(p) {
        return aabb.contains(p);
    };

    that.click = function(p) {
        callback(p);
    };

    that.render = function(context) {
        context.fillStyle = "#000";
        context.fillRect(aabb.minx, aabb.miny, aabb.width, aabb.height);

        context.font = "bold 12px sans-serif";
        context.fillStyle = "white";
        context.textBaseline = "middle";
        context.textAlign = "center";
        context.fillText(text, aabb.minx+aabb.width/2, aabb.miny+aabb.height/2);
    };

    return that;
}

function constructIOArray() {
    var i;
    var a = [];
    a.names = [];
    a.indexes = {};

    for (i = 0; i < arguments.length/2; i++) {
        a.names[i] = arguments[i*2];
        a.indexes[arguments[i*2]] = i;
        a[i] = arguments[i*2+1];
    }

    return a;
}

function constructNode(title) {
    var that;
    var aabb, titleaabb;
    var last_updated, update_in_progress;

    aabb = new AABB(10,10,100,200);
    titleaabb = new AABB(10,10,100,20);

    that = {};
    that.inputs = [];
    that.outputs = [];
    
    that.render = function (context, t, dt) {
        var i, input_name, output_name, y;

        context.fillStyle = "black";
        context.fillRect(aabb.minx, aabb.miny, aabb.width, aabb.height);

        context.fillStyle = "white";
        context.fillRect(aabb.minx+2, aabb.miny+2, aabb.width-4, 20);

        context.fillStyle = "black";
        context.font = "bold 12px sans-serif";
        context.textBaseline = "middle";
        context.textAlign = "center";
        context.fillText(title, titleaabb.minx+titleaabb.width/2, titleaabb.miny+titleaabb.height/2);

        for (i = 0; i < that.inputs.length; i++) {
            input_name = that.inputs.names[i];
            y = 30 + i*22;

            context.fillStyle = "white";
            context.fillRect(aabb.minx+2, aabb.miny+2+y, 40, 20);

            context.fillStyle = "black";
            context.font = "bold 12px sans-serif";
            context.textBaseline = "top";
            context.textAlign = "left";
            context.fillText(input_name, aabb.minx+2, aabb.miny+2+y);
        }

        for (i = 0; i < that.outputs.length; i++) {
            output_name = that.outputs.names[i];
            y = 30 + i*22;

            context.fillStyle = "white";
            context.fillRect(aabb.maxx-2-40, aabb.miny+2+y, 40, 20);

            context.fillStyle = "black";
            context.font = "bold 12px sans-serif";
            context.textBaseline = "top";
            context.textAlign = "left";
            context.fillText(output_name, aabb.maxx-2-40, aabb.miny+2+y);
        }
    };

    that.checkDragHandle = function(p) {
        return titleaabb.contains(p);
    };

    that.pickup = function(p) {
        lastpos = p;
    };

    that.drag = function(p) {
        aabb.move(p.x-lastpos.x, p.y-lastpos.y);
        titleaabb.move(p.x-lastpos.x, p.y-lastpos.y);
        lastpos = p;
    };

    that.drop = function(p) {
        // nothin'
    };

    // dataflow handling
    that.dependencies = [];
    last_updated = -1;
    update_in_progress = false;

    that.addDependency = function(other_node) {
        that.dependencies.push(other_node);
    }

    /*
    clients = [];
    that.addClient = function(other_node) {
        that.clients.push(other_node);
    }
    */

    that.isOutOfDate = function(new_stamp) {
        if (last_updated < new_stamp) {
            return true;
        } else {
            return false;
        }
    }

    that.markUpdatePending = function() {
        update_in_progress = true;
    }

    that.isUpdatePending = function() {
        return update_in_progress;
    }

    that.markUpdated = function(new_stamp) {
        last_updated = new_stamp;
        update_in_progress = false;
    }

    return that;
}

function constructWaveNode() {
    var that;
    var super_render;

    var amplitude = 50;

    that = constructNode("wave!");

    super_render = that.render;

    that.render = function (context, t, dt) {
        super_render(context, dt, dt);
        context.fillStyle = "white";
        context.font = "bold 12px sans-serif";
        context.textBaseline = "middle";
        context.textAlign = "center";
    };

    that.outputs = constructIOArray("osc", 0);
    that.update = function (context, t, dt) {
        that.outputs[osc] = Math.sin(t)*amplitude;
    };

    return that;
}

function constructImageNode() {
    var that;
    var super_render;

    that = constructNode("image!");
    
    super_render = that.render;

    that.render = function (context, t, dt) {
        super_render (context, t, dt);

        // imagine this is an image
    };

    var g = function(name) {return that.inputs[that.inputs.indexes[name]];};

    that.update = function (context, t, dt) {
        context.strokeStyle = g('color');
        context.beginPath();
        context.arc(g('x')+128, g('y')+128, g('scale')*30, 0, Math.PI*2, false);
        context.closePath();
        context.stroke();
    };

    that.inputs = constructIOArray("x", 0, "y", 0, "scale", 1.0, "color", "red");

    return that;
}

// the pre-built backend, main execution environment
function constructBasicBackend() {
var that;
var buttons;
var nodes;
var pipes;
var dragging;

var active_pipe;

that = {};

dragging = null;
active_pipe = {};

buttons = [];
nodes = [];
pipes = [];

buttons = [
    constructButton({
        text:   "wave",
        aabb:   new AABB(0,100,50,50),
        callback: function() {
            nodes.push(constructWaveNode());
        }
    }),

    constructButton({
        text:   "image",
        aabb:   new AABB(0,170,50,50),
        callback: function() {
            nodes.push(constructImageNode());
        }
    }),
];

nodes = [];
pipes = [];


/*
// init arrows (load from localStorage if possible)
arrows = (function () {
    var arrows, arrowcount;

    if (!supports_html5_storage()) {
        return [];
    }

    arrowcount = parseInt(localStorage["test.arrowcount"]);
    if (isNaN(arrowcount)) {
        arrowcount = 0;
    }


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
*/

// public methods
that.mouseClick = function (p) {
    var i;

    // nodes are in front of buttons
    for (i = nodes.length-1; i >= 0; i--) {
        if (nodes[i].checkDragHandle(p)) {
            return false;
        }
    }

    for (i = buttons.length-1; i >= 0; i--) {
        if (buttons[i].contains(p)) {
            buttons[i].click(p);
            return true;
        }
    }

    return false;
};

that.mousePickup = function (p) {
    var i;
    for (i = nodes.length-1; i >= 0; i--) {
        if (nodes[i].checkDragHandle(p)) {
            nodes[i].pickup(p);

            dragging = nodes[i];
            return true;
        }
    }
    return true;
};

that.mouseDrag = function (p) {
    if (!!dragging) {
        dragging.drag(p);
    }
    return true;
};

that.mouseDrop = function (p) {
    if (!!dragging) {
        dragging.drop(p);
        dragging = null;
    }
    return true;
};

that.render = function(canvasElement, context, t, dt) {
    var w = canvasElement.width;
    var h = canvasElement.height;
    var i;

    context.clearRect(0, 0, w, h);

/*
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
    */

    // render buttons
    for (i = 0; i < buttons.length; i++) {
        buttons[i].render(context);
    }

    // render nodes
    for (i = 0; i < nodes.length; i++) {
        nodes[i].render(context, t, dt);
    }

    // keep runnning me
    return true;
};

that.getInterpreter = function () {
    var interpreterBackend;

    interpreterBackend = {}

    interpreterBackend.render = function (canvasElement, context, t, dt) {
        var node_queue = [];
        var i, n, j, d, exploring_dependencies;
        context.clearRect(0, 0, 256, 256);

        // TODO: should just compute an "update order" on every graph edit,
        // that's kind of the point of this whole project

        // first queue up those with no "output", these must be displaying something
        for (i = 0; i < nodes.length; i++) {
            if (nodes[i].outputs.length == 0) {
                node_queue.push(nodes[i]);
                nodes[i].markUpdatePending();
            }
        }

        while (node_queue.length > 0) {
            dirty_dependencies = false;
            n = node_queue.shift();
            if (n.isOutOfDate(t)) {
                for (j = 0; j < n.dependencies; j++) {
                    d = n.dependencies[j];
                    if (!d.isUpdatePending()) {
                        exploring_dependencies = true;
                        node_queue.push(d);
                        d.markUpdatePending();
                    }
                }

                if (exploring_dependencies) {
                    node_queue.push(d);
                } else {
                    n.update(context, t, dt);
                    n.markUpdated(t);
                }
            }
        }
        return true;
    };

    return interpreterBackend;
};

return that;

}; // end of BasicBackend
