Deferred {
    var value, error, resolved=\unresolved, waitingThreads;
    
    *new {
        ^super.new
    }
    
    *using {
        |func, clock|
        var d = Deferred();
        ^d.using(func, clock)
    }
    
    *serverSync {
        |func, server=(Server.default)|
        ^this.using {
            var result = func.value();
            server.sync;
            result;
        }
    }
    
    *newResolved {
        |value|
        ^this.new.value_(value)
    }
    
    completionMsg {
        |server|
        var id, resp, 
            creationTime = Process.monotonicClockTime;
        
        server = server ?? { Server.default };
        id = UniqueID.next;
        resp = OSCFunc({
            |msg|
            resp.free;
            this.value = Process.monotonicClockTime - creationTime;
        }, '/synced', 
            server.addr, 
            argTemplate: [id]
        );
        ^[\sync, id];
    }
    
    asDeferred {
        ^this
    }
    
    wait {
        |timeout|
        this.prWait(timeout);
        
        if (resolved == \error) {
            error.throw;
        };
        
        ^value
    }
    
    hasValue {
        ^(resolved == \value)
    }
    
    hasError {
        ^(resolved == \error)
    }
    
    isResolved {
        ^(resolved != \unresolved);
    }
    
    value {
        switch(resolved,
            \value, 		{ ^value },
            \error, 		{ error.throw },
            \unresolved,	{ AccessingDeferredValueError().throw }
        );
    }
    
    value_{
        |inValue|
        if (resolved == \unresolved) {
            resolved = \value;
            value = inValue;
            this.prResume();
        } {
            ResettingDeferredValueError(value, error).throw;
        }
    }
    
    error {
        // @TODO - do we want to error here if no error was provided?
        ^error
    }
    
    error_{
        |inError|
        if (resolved == \unresolved) {
            resolved = \error;
            error = inError;
            this.prResume();
        } {
            ResettingDeferredValueError(value, error).throw;
        }
    }
    
    valueCallback {
        ^{
            |value|
            this.value = value;
        }
    }
    
    errorCallback {
        ^{
            |error|
            this.error = error;
        }
    }
    
    prCallFunction {
        |function|
        var result = \noValue;
        var env = currentEnvironment;
        // We want to catch errors that happen when function is called, but AVOID catching errors
        // that may occur during the this.value set operation.
        try {
            result = function.value(this);
            
            if (result.isKindOf(Deferred)) {
                result = result.wait();
            };
            
            // gotcha: returning an Exception from a function wrapped in a try effectively throws.
            nil;
        } {
            |error|
            this.error = error;
        };
        
        if (result != \noValue) {
            this.value = result;
        }  
    }
    
    using {
        |function, clock, fork=false|
        clock = clock ?? { thisThread.clock };
        if (fork) {
            { this.prCallFunction(function) }.fork(clock)
        } {
            {
                this.prCallFunction(function)
            }.forkIfNeeded(clock);
        }
    }
    
    then {
        |valueFunc, errorFunc, clock, timeout|
        var newDeferred, result, errResult, handleFunc;
        
        clock = clock ?? { thisThread.clock };
        // If not specified, just default to returning / rethrowing whatevers passed in
        valueFunc = valueFunc ? { |v| v };
        errorFunc = errorFunc ? { |v| v.throw; };
        
        newDeferred = Deferred();
        
        handleFunc = {
            if (this.hasValue) {
                newDeferred.using({
                    valueFunc.value(this.value);
                }, clock)
            } {
                // SUBTLE: If we throw in value-handling code, it's turned into an error.
                // If we throw during ERROR-handling code, we immediately fail and don't
                // continue the chain. Otherwise, Error return values are passed along
                // as errors, everything else as value.
                errResult = errorFunc.value(this.error);
                
                newDeferred.using({
                    if (errResult.isKindOf(Exception)) {
                        errResult.throw;
                    } {
                        errResult;
                    }
                }, clock)
            }
        };
        
        if (this.isResolved) {
            handleFunc.()
        } {
            {
                this.prWait(timeout);
                handleFunc.();
            }.fork(clock ?? thisThread.clock);
        };
        
        ^newDeferred
    }
    
    onValue {
        |function, clock|
        this.then(function, nil, clock);
    }
    
    onError {
        |function, clock|
        this.then(nil, function, clock);
    }
    
    done {
        this.then {
            |value|
            var stream = CollStream("Deferred[%].value: ".format(this.identityHash));
            value.printOn(stream);
            stream.collection;
        } {
            |error|
            error.throw;
        }
    }
    
    prWait {
        |timeout|
        if (resolved == \unresolved) {
            waitingThreads = waitingThreads.add(thisThread.threadPlayer);
            
            if (timeout.notNil) {
                thisThread.clock.sched(timeout, {
                    if (resolved == \unresolved) {
                        this.error = DeferredTimeoutError(value, error).timeout_(timeout);
                    }
                })
            };
            
            while { resolved == \unresolved } {
                \hang.yield;
            };
            
            waitingThreads.remove(thisThread.threadPlayer);
        }
    }
    
    prResume {
        waitingThreads.do {
            |thread|
            thread.clock.sched(0, thread);
        };
    }
    
    printOn {
        |stream|
        stream << "Deferred(%)".format(this.identityHash);
    }
    
    dump {
        this.postln;
        "  hasValue = %".format(this.hasValue).postln;
        "  hasError = %".format(this.hasError).postln;
        "  isResolved = %".format(this.isResolved).postln;
        if (this.hasValue) {
            "  value = %".format(this.value).postln;
        };
        if (this.hasError) {
            "  error = %".format(this.error).postln;
        };
        "  waiting thread count = %".format(waitingThreads.size).postln;
    }
}

ResettingDeferredValueError : Error {
    var <>value, <>error;
    
    *new {
        |value, error|
        ^super.new(
            "Setting a Deferred value after the value has already been set. (value:%, error:%)".format(value, error)
        ).value_(value).error_(error);
    }
}

AccessingDeferredValueError : Error  {
    errorString {
        ^"Accessing Deferred value before the value has been set.";
    }
}

DeferredTimeoutError : Error  {
    var <>timeout;
    
    *new {
        |timeout|
        ^super.newCopyArgs(timeout);
    }
    
    errorString {
        ^"Timed out waiting for Deferred after % seconds.".format(timeout)
    }
}

+Object {
    // Interpretation: if we have a normal object and need a deferred, just
    // construct an already-resolved Deferred.
    asDeferred {
        ^Deferred.newResolved(this)
    }
}

+Synth {
    *doNew {
        |...args|
        ^Deferred().using({
            var obj;
            obj = Synth(*args);
            obj.server.sync;
            obj;
        });
    }
}

+Group {
    *doNew {
        |...args|
        ^Deferred().using({
            var obj;
            obj = Group(*args);
            obj.server.sync;
            obj;
        })
    }
}

+SynthDef {
    doAdd {
        |...args|
        ^Deferred().using({
            this.add(*args);
            Server.default.sync;
            this;
        })
    }    
    
    doRemove {
        |libname=\default|
        ^Deferred().using({
            SynthDef.removeAt(this.name);
            SynthDescLib.getLib(libname).servers.collect(_.doSync).do(_.wait);
            true;
        })
    }
}

+Bus {
    *doNew {
        |...args|
        ^Deferred().using({
            var obj;
            obj = Bus(*args);
            obj.server.sync;
            obj;
        })
    }
    
    doGet {
        var deferred = Deferred();
        this.get(deferred.valueCallback);
        ^deferred
    }
}

+Buffer {
    *doAlloc {
        |key, numFrames = 2048, numChannels = 1|
        ^Deferred().using({
            var obj = Buffer.alloc(key, numFrames, numChannels);
            obj.server.sync;
            obj;
        })
    }
    
    *doRead {
        |server, path, startFrame = 0, numFrames = -1, bufnum|
        var d = Deferred();
        Buffer.read(server, path, startFrame, numFrames, d.valueCallback, bufnum);
        ^d
    }
}

+Server {
    doBoot {
        |...args|
        var deferred = Deferred();
        this.waitForBoot({
            deferred.value = this
        });
        ^deferred
    }
    
    doQuit {
        |...args|
        var deferred = Deferred();
        this.quit({
            deferred.value = this
        }, {
            deferred.error = "Server failed to quit"
        });
        ^deferred
    }
    
    doSync {
        ^Deferred.using({
            this.sync.wait;
        })
    }
}




