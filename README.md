# CoinCompanyEconomy-Plugin
A company minecraft plugin that uses coin API 0-0


Dependencies:

CoinCard - https://www.spigotmc.org/resources/coin-card.129770/

Vault - https://www.spigotmc.org/resources/vault.34315/

An economy plugin - https://www.spigotmc.org/resources/cmi-300-commands-insane-kits-portals-essentials-economy-mysql-sqlite-much-more.3742/

Free One - https://essentialsx.net/downloads


Commands:

/company 

/company info

/company hire

/company fire

/company leave

/company reload (OP)


No permissions.

Default company:

```
displayName: Default Company
card: e1301fadfc35
commands:
  on-fire:
  - say %player% has been fired!
groups:
  '1':
    tag: Owner
    salary: 1.0E-8
    permissions:
      can-hire: true
      can-fire: true
      can-deposit: true
      can-withdraw: true
    commands:
      on-hire:
      - say %player% is now the owner!
contract:
  enabled: true
  auto-send-on-hire: true
  lines:
  - '&6Employment Contract - Default Company'
  - '&7--------------------------------------'
  - '&7You agree to follow company rules.'
  - '&7Breaking rules may result in termination.'
  - '&aSalary will be paid every 30 minutes.'
  - '&7--------------------------------------'
data:
  Steve:
    group: 1

```
