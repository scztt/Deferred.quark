/*
UnitTest.reset; UnitTestScript("Deferred", thisProcess.nowExecutingPath).runScript;
*/

{
	|test|
	var d, expected, expectedError, result, timeout;

	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expected = 100;

	d = Deferred();

	d.value = expected;
	result = d.value;

	test.assertEquals(result, expected, ".value return correct value");
	test.assert(d.hasValue);
	test.assert(d.hasError.not);
	// test.assert(d.isResolved);

	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expected = nil;
	expectedError = Error("Problem detected");

	d = Deferred();

	d.error = expectedError;

	try {
		result = d.value;
	} {
		|resultError|
		test.assertEquals(resultError, expectedError, ".value access after an error throws the error");
		test.assertEquals(result, expected, ".value access after an error does not return a value");
	};


	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expected = 100;

	d = Deferred();

	d.valueCallback.value(expected);
	result = d.value;

	test.assertEquals(result, expected, "calling .valueCallback sets value");


	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expectedError = Error("Problem detected");

	d = Deferred();

	d.errorCallback.value(expectedError);
	result = d.error;

	test.assertEquals(result, expectedError, "calling .errorCallback sets error");


	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expected = nil;
	expectedError = Error("Problem detected");

	d = Deferred();

	d.error = expectedError;

	try {
		result = d.value;
	} {
		|resultError|
		test.assertEquals(resultError, expectedError, ".value access after an error throws the error");
		test.assertEquals(result, expected, ".value access after an error does not return a value");
	};


	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expected = nil;
	expectedError = Error("Problem detected");

	d = Deferred();

	d.error = expectedError;
	result = d.error;

	test.assertEquals(result, expectedError, ".error return correct error object");
	test.assert(d.hasValue.not);
	test.assert(d.hasError);
	test.assert(d.isResolved);


	////////////////////////////////////////////////////////////////////////////////
	expected = 100;
	result = nil;
	d = Deferred();

	{ d.value = expected }.defer(0.1);
	result = d.wait;

	test.assertEquals(result, expected, "Wait returns correct value");
	test.assert(d.hasValue);
	test.assert(d.hasError.not);
	test.assert(d.isResolved);

	////////////////////////////////////////////////////////////////////////////////
	expected = nil;
	result = nil;
	expectedError = Error("Problem detected");
	d = Deferred();

	{ d.error = expectedError }.defer(0.1);

	try {
		result = d.wait;
	} {
		|resultError|
		test.assertEquals(resultError, expectedError, "Wait returns correct error value");
		test.assertEquals(result, expected, "Wait does not return value in error case");

		test.assert(d.hasValue.not);
		test.assert(d.hasError);
		test.assert(d.isResolved);
	};

	////////////////////////////////////////////////////////////////////////////////
	expected = nil;
	result = nil;
	timeout = 1;
	d = Deferred();

	{ d.value = expected }.defer(0.1);

	result = d.wait(timeout);
	test.assertEquals(result, expected, "Wait returns correct value with timeout");

	////////////////////////////////////////////////////////////////////////////////
	expected = nil;
	result = nil;
	timeout = 0.1;
	d = Deferred();

	try {
		result = d.wait(timeout);
	} {
		|resultError|
		test.assertEquals(resultError.class, DeferredTimeoutError, "Wait fails after timeout");
	};


	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expected = 100;

	d = Deferred.newResolved(expected);
	result = d.wait;

	test.assertEquals(result, expected, "Wait returns correct value");



	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expected = 100;

	d = Deferred.newResolved(expected);
	result = d.wait;

	test.assertEquals(result, expected, "Wait returns correct value");


	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expected = 100;

	d = expected.asDeferred;
	result = d.wait;

	test.assertEquals(d.class, Deferred, ".asDeferred creates a Deferred");
	test.assertEquals(result, expected, ".asDeferred resolves a Deferred to the value");

	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expected = 100;

	d = Deferred();
	d.using {
		0.1.wait;
		expected
	};

	result = d.wait;

	test.assertEquals(result, expected, "return value of .using function is set to Deferred:value");


	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expectedError = Error("Problem detected");

	d = Deferred();
	d.using {
		0.1.wait;
		expectedError.throw;
	};

	try {
		result = d.wait;
	} {
		|resultError|
		test.assertEquals(resultError, expectedError, "error throwb by .using function is set to Deferred:error");
	};

	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expected = 100;

	d = Deferred();
	d.using {
		0.1.wait;

		Deferred.using({
			0.1.wait;
			expected
		})
	};

	result = d.wait;

	test.assertEquals(result, expected, "returning a Deferred from a .using function waits for that Deferred value");

	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expected = 100;

	d = Deferred();
	d.then {
		|thenResult|
		result = thenResult;
		test.assertEquals(result, expected, "Deferred value is passed to the .then function");
	};

	d.value = expected;


	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expected = 100;

	d = Deferred();
	d.value = expected;
	d.then {
		|thenResult|
		result = thenResult;
		test.assertEquals(result, expected, "Deferred value is passed to the .then function even if it's set first");
	};


	////////////////////////////////////////////////////////////////////////////////
	result = nil;
	expectedError = Error("Problem detected");

	d = Deferred();
	d.then(
		valueFunc: {
			|value|
			test.assert(false, "Value func should not be called in case of an error.")
		},
		errorFunc: {
			|thenError|
			result = thenError;
			test.assertEquals(result, expectedError, "Deferred value is passed to the .then function");
		}
	);

	d.error = expectedError;

}
