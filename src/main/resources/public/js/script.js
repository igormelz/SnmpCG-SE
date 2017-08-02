    
    
    // fmtbytes 
    function fmtBytes(bytes,fmt) {
    if (fmt == null) {
    	if (bytes > 1000*1000*1000) {
    		return (bytes / (1000*1000*1000)).toFixed(1)+' GB';
    	} else if (bytes > 1000*1000) {
    		return (bytes / (1000*1000)).toFixed(1)+' MB';
    	} else if (bytes > 1000) {
    		return (bytes / 1000).toFixed(1)+' kB';
    	}
    	return bytes + ' B';
    } else {
    	switch(fmt){
    	case 'MB': 
    		return (bytes / (1000*1000)).toFixed(1);
    	case 'GB':
    		return (bytes / (1000*1000*1000)).toFixed(1);
    	case 'kB':
    		return (bytes / 1000).toFixed(1);
    	default:
    		return bytes;
    	}
    }
    }
    
    // calcRate 
    function bandwidth(bytes,duration) {
    	if (bytes == 0) {
    		return 0;
    	}
    	var bps = (Number.parseInt(bytes) * 800) / duration;
    	/*
    	if (bps > 1000*1000*1000) {
    		return (bps/(1000*1000*1000)).toFixed(1)+" Gbit/s";
    	} else if (bps > 1000*1000) {
    		return (bps/(1000*1000)).toFixed(1)+" Mbit/s";
    	} else if (bps > 1000) {
    		return (bps/(1000)).toFixed(1)+" kbit/s";
    	}
    	return (bps).toFixed(1);
    	*/
    	return (bps/(1000*1000)).toFixed(1);
    }
    
    function pad(v) {
    	return v<10?"0"+v:v;
    }
    
    function validateIPaddress(ipaddress) {
        if (/^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/.test(ipaddress)) {
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
    	var s= Math.floor( tt / 100);
    	tt %= 100;
    	return d+" days, "+pad(h)+":"+pad(m)+":"+pad(s)+"."+tt; 
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