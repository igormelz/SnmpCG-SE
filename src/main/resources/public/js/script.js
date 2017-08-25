bootstrap_alert = function() {
}

bootstrap_alert.info = function(message, timeout) {
	$("div#action-success").html("<p>" + message + "</p>");
	$("div#action-success").show();
	$("div#action-success").fadeOut(timeout);
}

bootstrap_alert.error = function(message, timeout) {
	$("div#action-error").html("<p>" + message + "</p>");
	$("div#action-error").show();
	$("div#action-error").fadeOut(timeout);
}

var clusterInfo = function() {
	$.ajax({
		url : "/api/v1/cluster/nodeStatus",
		success : function(answer) {
			$('#clusterInfo').text(answer.Status);
		}
	});
}

function showTagsInfo(tags) {
	var answer = "";
	Object.keys(tags).forEach(function(item) {
		answer += "<abbr title='" + item + "'>" + tags[item] + "</abbr>&nbsp;";
	});
	return answer;
}

function getChargingStats() {
	$.ajax({
		url : "/api/v1/interfaces?stats",
		success : function(stats) {
			$("#chargeableCount").text(stats.chargeableCount);
			$("#traceCount").text(stats.traceCount);
		}
	});
}


function enableDefaultValue(e) {
	if (e) {
		$('#community').prop('disabled', true);
		$('#retries').prop('disabled', true);
		$('#timeout').prop('disabled', true);
	} else {
		$('#community').prop('disabled', false);
		$('#retries').prop('disabled', false);
		$('#timeout').prop('disabled', false);
	}
}

function addslashes(str) {
	return (str + '').trim().replace(/[\\"']/g, '\\$&').replace(/\u0000/g,
			'\\0');
}

// fmtbytes
function fmtBytes(bytes, fmt) {
	if (fmt == null) {
		if (bytes > 1000 * 1000 * 1000) {
			return (bytes / (1000 * 1000 * 1000)).toFixed(1) + ' GB';
		} else if (bytes > 1000 * 1000) {
			return (bytes / (1000 * 1000)).toFixed(1) + ' MB';
		} else if (bytes > 1000) {
			return (bytes / 1000).toFixed(1) + ' kB';
		}
		return bytes + ' B';
	} else {
		switch (fmt) {
		case 'MB':
			return (bytes / (1000 * 1000)).toFixed(1);
		case 'GB':
			return (bytes / (1000 * 1000 * 1000)).toFixed(1);
		case 'kB':
			return (bytes / 1000).toFixed(1);
		default:
			return bytes;
		}
	}
}

// calcRate
function bandwidth(bytes, duration) {
	if (bytes == 0) {
		return 0;
	}
	var bps = (Number.parseInt(bytes) * 800) / duration;
	/*
	 * if (bps > 1000*1000*1000) { return (bps/(1000*1000*1000)).toFixed(1)+"
	 * Gbit/s"; } else if (bps > 1000*1000) { return
	 * (bps/(1000*1000)).toFixed(1)+" Mbit/s"; } else if (bps > 1000) { return
	 * (bps/(1000)).toFixed(1)+" kbit/s"; } return (bps).toFixed(1);
	 */
	return (bps / (1000 * 1000)).toFixed(1);
}

function pad(v) {
	return v < 10 ? "0" + v : v;
}

function validateIPaddress(ipaddress) {
	if (/^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/
			.test(ipaddress)) {
		return (true)
	}
	return (false)
}

function uptime(timeticks) {
	var tt = timeticks;
	var d = Math.floor(tt / 8640000);
	tt %= 8640000;
	var h = Math.floor(tt / 360000);
	tt %= 360000;
	var m = Math.floor(tt / 6000);
	tt %= 6000;
	var s = Math.floor(tt / 100);
	tt %= 100;
	return d + " days, " + pad(h) + ":" + pad(m) + ":" + pad(s) + "." + tt;
}

function getUrlParameter(sParam) {
	var sPageURL = window.location.search.substring(1);
	var sURLVariables = sPageURL.split('&');
	for (var i = 0; i < sURLVariables.length; i++) {
		var sParameterName = sURLVariables[i].split('=');
		if (sParameterName[0] == sParam) {
			return sParameterName[1];
		}
	}
}

// format charging interfaces
function formatChargingInterface(ifEntry) {
	// source
	ifEntry.sourceName = "<a title=\"click to view interfaces\" href=\"view.html?ip="
			+ ifEntry.source.ipAddress
			+ "\"><span class=\"glyphicon glyphicon-list\" aria-hidden=\"true\"></span></a>";
	ifEntry.sourceName += "&nbsp;<span>"
			+ ifEntry.source.ipAddress
			+ ((ifEntry.source.sysName) ? "<br><small>("
					+ ifEntry.source.sysName + ")</small></span>" : "</span>");
	ifEntry.sourceInfo = showTagsInfo(ifEntry.source.tags);

	ifEntry.status = (ifEntry.up) ? "<lable class='label label-info'><span class=\"glyphicon glyphicon-ok-sign\"></span> Up</label>"
			: "<label class='label label-warning'><span class='glyphicon glyphicon-exclamation-sign' title='down'></span> Down</label>";

	ifEntry.portType = (ifEntry.portType == 0) ? "<span class='label label-primary'>PE Downlink</span>"
			: "<span class='label label-info'>CPE Uplink</span>";

	ifEntry.chargeInfo = showTagsInfo(ifEntry.tags);

	ifEntry.action = "<a href='#' title=\"click to trace off\" id='traceBtn'>";
	if (ifEntry.trace) {
		ifEntry.action += "<span class=\"glyphicon glyphicon-check\" aria-hidden=\"true\"></span></a>";
	} else {
		ifEntry.action += "<span class=\"glyphicon glyphicon-unchecked\" aria-hidden=\"true\"></span></a>";
	}

}
