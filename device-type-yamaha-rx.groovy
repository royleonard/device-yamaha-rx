/**
 *  Yamaha Network Receiver
 *     Works on RX-V*
 *    SmartThings driver to connect your Yamaha Network Receiver to SmartThings
 *
 *  Loosely based on: https://github.com/BirdAPI/yamaha-network-receivers
 *   and: http://openremote.org/display/forums/Controlling++RX+2065+Yamaha+Amp
 */

preferences {
	input("destIp", "text", title: "IP", description: "The device IP")
    input("destPort", "number", title: "Port", description: "The port you wish to connect")
    input("inputChan","enum", title: "Input Control", description: "Select the inputs you want to use", options: ["TUNER","MULTI CH","PHONO","HDMI1","HDMI2","HDMI3","HDMI4","HDMI5","HDMI6","HDMI7","AV1","AV2","AV3","AV4","AV5","AV6","AV7","V-AUX","AUDIO","AUDIO1","AUDIO2","AUDIO3","AUDIO4","NET","Rhapsody","SIRIUS IR","Pandora","SERVER","NET RADIO","USB","iPod (USB)","AirPlay"],multiple: true,required: true)
    input("Zone","enum", title: "Zone", description: "Select the Zone you want to use", options: ["Main_Zone","Zone_2"],multiple: false,required: true)
}
 

metadata {
	definition (name: "Yamaha Network Receiver", namespace: "KristopherKubicki", 
    	author: "kristopher@acm.org") {
        capability "Actuator"
	capability "Switch" 
        capability "Polling"
        capability "Switch Level"
        
        attribute "mute", "string"
        attribute "input", "string"
        attribute "inputChan", "enum"

        command "mute"
        command "unmute"
        command "inputSelect", ["string"]
        command "inputNext"
        command "toggleMute"

      	}

	simulator {
		// TODO-: define status and reply messages here
	}
	tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: false, canChangeBackground: true) {
            state "on", label: '${name}', action:"switch.off", backgroundColor: "#79b821", icon:"st.Electronics.electronics16"
            state "off", label: '${name}', action:"switch.on", backgroundColor: "#ffffff", icon:"st.Electronics.electronics16"
        }
		standardTile("poll", "device.poll", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
			state "poll", label: "", action: "polling.poll", icon: "st.secondary.refresh", backgroundColor: "#FFFFFF"
		}
        standardTile("input", "device.input", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
			state "input", label: '${currentValue}', action: "inputNext", icon: "", backgroundColor: "#FFFFFF"
		}
        standardTile("mute", "device.mute", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
            state "muted", label: '${name}', action:"unmute", backgroundColor: "#79b821", icon:"st.Electronics.electronics13"
            state "unmuted", label: '${name}', action:"mute", backgroundColor: "#ffffff", icon:"st.Electronics.electronics13"
		}
        controlTile("level", "device.level", "slider", height: 1, width: 2, inactiveLabel: false, range: "(0..100)") {
			state "level", label: '${name}', action:"setLevel"
		}

		main "switch"
        details(["switch","input","mute","level","poll"])
	}
}



def parse(String description) {
 	def map = stringToMap(description)
    log.debug ("Parse started")
    if(!map.body) { return }
	def body = new String(map.body.decodeBase64())

	def statusrsp = new XmlSlurper().parseText(body)
    log.debug ("Parse got body ${body}...")
	def power = statusrsp.$Zone.Basic_Status.Power_Control.Power.text()
    log.debug ("$Zone Power - ${power}")
    if(power == "On") { 
    	sendEvent(name: "switch", value: 'on')
    }
    if(power != "" && power != "On") { 
    	sendEvent(name: "switch", value: 'off')
    }
  
    def inputChan = statusrsp.$Zone.Basic_Status.Input.Input_Sel.text()
    log.debug ("$Zone Input - ${inputChan}")
 	if(inputChan != "") { 
    	sendEvent(name: "input", value: inputChan)
	}

    def muteLevel = statusrsp.$Zone.Basic_Status.Volume.Mute.text()
    log.debug ("$Zone Mute - ${muteLevel}")
    if(muteLevel == "On") { 
    	sendEvent(name: "mute", value: 'muted')
	}
    if(muteLevel != "" && muteLevel != "On") {
	    sendEvent(name: "mute", value: 'unmuted')
    }

    if(statusrsp.$Zone.Basic_Status.Volume.Lvl.Val.text()) { 
    	def int volLevel = statusrsp.$Zone.Basic_Status.Volume.Lvl.Val.toInteger() ?: -250
        volLevel = sprintf("%d",(((volLevel + 800) / 9)/5)*5)
   		def int curLevel = 65
        try {
        	curLevel = device.currentValue("level")
        } catch(NumberFormatException nfe) { 
        	curLevel = 65
        }
        if(curLevel != volLevel) {
           log.debug ("$Zone level - ${volLevel}")
    		sendEvent(name: "level", value: volLevel)
        }
    }

}

// Needs to round to the nearest 5
def setLevel(val) {
	sendEvent(name: "mute", value: "unmuted")     
    sendEvent(name: "level", value: val)
    
    	def scaledVal = sprintf("%d",val * 9 - 800)
    	scaledVal = (((scaledVal as Integer)/5) as Integer) * 5
    	request("<YAMAHA_AV cmd=\"PUT\"><$Zone><Volume><Lvl><Val>$scaledVal</Val><Exp>1</Exp><Unit>dB</Unit></Lvl></Volume></$Zone></YAMAHA_AV>")
}

def on() {
    log.debug "on"
	sendEvent(name: "switch", value: 'on')
	request("<YAMAHA_AV cmd=\"PUT\"><$Zone><Power_Control><Power>On</Power></Power_Control></$Zone></YAMAHA_AV>")
}

def off() {
    log.debug "off"
	sendEvent(name: "switch", value: 'off')
	request("<YAMAHA_AV cmd=\"PUT\"><$Zone><Power_Control><Power>Standby</Power></Power_Control></$Zone></YAMAHA_AV>")
}

def toggleMute(){
    if(device.currentValue("mute") == "muted") { unmute() }
	else { mute() }
}

def mute() { 
	sendEvent(name: "mute", value: "muted")
	request("<YAMAHA_AV cmd=\"PUT\"><$Zone><Volume><Mute>On</Mute></Volume></$Zone></YAMAHA_AV>")
}

def unmute() { 
	sendEvent(name: "mute", value: "unmuted")
	request("<YAMAHA_AV cmd=\"PUT\"><$Zone><Volume><Mute>Off</Mute></Volume></$Zone></YAMAHA_AV>")
}

def inputNext() { 

	def cur = device.currentValue("input")
	// modify your inputs right here! 
    def selectedInputs = ["TUNER","MULTI CH","PHONO","HDMI1","HDMI2","HDMI3","HDMI4","HDMI5","HDMI6","HDMI7","AV1","AV2","AV3","AV4","AV5","AV6","AV7","V-AUX","AUDIO","AUDIO1","AUDIO2","AUDIO3","AUDIO4","NET","Rhapsody","SIRIUS IR","Pandora","SERVER","NET RADIO","USB","iPod (USB)","AirPlay"]
    
    
    def semaphore = 0
    for(selectedInput in selectedInputs) {
    	if(semaphore == 1) { 
        	return inputSelect(selectedInput)
        }
    	if(cur == selectedInput) { 
        	semaphore = 1
        }
    }
}


def inputSelect(channel) {
 	sendEvent(name: "input", value: channel	)
    log.debug "Input $channel"
	request("<YAMAHA_AV cmd=\"PUT\"><$Zone><Input><Input_Sel>$channel</Input_Sel></Input></$Zone></YAMAHA_AV>")
}

def poll() { 
	refresh()
}

def refresh() {
    log.debug ("Refresh")
    request("<YAMAHA_AV cmd=\"GET\"><$Zone><Basic_Status>GetParam</Basic_Status></$Zone></YAMAHA_AV>")
}

def request(body) { 

    def hosthex = convertIPtoHex(destIp)
    def porthex = convertPortToHex(destPort)
    device.deviceNetworkId = "$hosthex:$porthex" 

    def hubAction = new physicalgraph.device.HubAction(
   	 		'method': 'POST',
    		'path': "/YamahaRemoteControl/ctrl",
        	'body': body,
        	'headers': [ HOST: "$destIp:$destPort" ]
		) 
        
    hubAction    
        
    hubAction
}


private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04X', port.toInteger() )
    return hexport
}
